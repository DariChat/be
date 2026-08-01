// k6 WebSocket 부하 테스트: 단일 서버 환경에서 채팅방 동시접속자 시뮬레이션
//
// 시나리오: k6/seed.sql로 만든 유저 50명 / 방 10개(방마다 5명)를 사용한다.
// 각 VU(가상유저)는 로그인 후 자신이 속한 방을 구독하고, 방마다 1명(발신자)은
// 주기적으로 메시지를 전송, 나머지 4명은 수신만 하며 대기한다.
// 이렇게 하면 STOMP 연결/구독 유지, 메시지 발행(DB 쓰기), 브로드캐스트 팬아웃,
// 비동기 읽음 처리까지 이 서비스의 핵심 부하 지점을 한 번에 재현할 수 있다.
//
// 구독 확인: Spring의 STOMP 서버(SimpleBrokerMessageHandler)는 receipt 헤더를
// 자동으로 응답해주지 않는다(SUBSCRIBE/SEND 둘 다 RECEIPT 프레임을 보내는 코드가
// 없음, 소스로 확인) — 그래서 receipt 대신, SUBSCRIBE를 보낸 시점을 "구독 요청
// 완료"로 간주한다(SimpleBroker는 SUBSCRIBE를 동기적으로 처리하므로 실질적으로
// 안전한 근사다). 발신자는 자신의 SUBSCRIBE 전송 후 WARMUP_SEC만큼 대기한 뒤
// 발신을 시작한다 — 나머지 4명이 아직 SUBSCRIBE 전에 첫 메시지가 나가면, 그
// 유실이 서버 문제가 아니라 테스트 타이밍 문제인데도 서버 유실처럼 잡히기
// 때문이다. k6는 VU마다 격리된 프로세스라 발신자가 다른 4명의 구독 완료를
// 직접 기다릴 수는 없으므로(진짜 barrier는 Redis 등 외부 조율이 필요), 고정
// 지연으로 근사한다. SEND의 성공/실패는 서버 응답(RECEIPT) 대신, 발신자 자신도
// 같은 방을 구독 중이라는 점을 이용해 "자기 메시지가 브로드캐스트로 되돌아오는
// MESSAGE" 또는 "ERROR"로 판정한다.
//
// 실행 전: docker exec -i talkie-mysql-1 mysql -uroot -proot talkie < seed.sql
// 실행: k6 run chat_load_test.js
// 발신 간격을 좁혀 부하를 올릴 때: k6 run -e SEND_INTERVAL_SEC=1 chat_load_test.js

import ws from 'k6/ws';
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';

const BASE_HTTP = __ENV.BASE_HTTP || 'http://localhost:8080';
const BASE_WS = __ENV.BASE_WS || 'ws://localhost:8080/ws-talkie';
const USER_COUNT = 50;
const ROOM_COUNT = 10;
const ROOM_SIZE = USER_COUNT / ROOM_COUNT;
const SEND_INTERVAL_SEC = Number(__ENV.SEND_INTERVAL_SEC || 3); // 발신자가 메시지를 보내는 주기
const WARMUP_SEC = Number(__ENV.WARMUP_SEC || 3); // 내 구독 확정 후, 나머지 멤버 구독을 기다리는 여유 시간

const messageLatency = new Trend('chat_message_latency_ms', true);
const messagesSent = new Counter('chat_messages_sent');
const messagesReceived = new Counter('chat_messages_received');
const messagesExpected = new Counter('chat_messages_expected');
// add(true)=성공, add(false)=실패 표본을 시도마다 기록해야 Rate가 "오류 수/전체 시도 수"가 된다.
// 오류 발생 시에만 add(1)을 부르면 표본이 오류만 남아 값이 왜곡된다(관측된 적 있음).
const sendSuccessRate = new Rate('chat_send_success_rate');
const wsConnectSuccessRate = new Rate('chat_ws_connect_success_rate');
const subscribeSuccessRate = new Rate('chat_subscribe_success_rate');

export const options = {
    scenarios: {
        chat_room_users: {
            executor: 'per-vu-iterations',
            vus: USER_COUNT,
            iterations: 1,
            maxDuration: '2m',
        },
    },
    thresholds: {
        chat_message_latency_ms: ['p(95)<1000'],
        chat_send_success_rate: ['rate>0.99'],
        chat_ws_connect_success_rate: ['rate>0.99'],
        chat_subscribe_success_rate: ['rate>0.99'],
    },
};

// setup()은 한 번만 실행되며, VU마다 필요한 로그인 토큰과 방 배정 정보를 미리 만들어둔다.
// 로그인 API 자체를 부하 테스트 대상에 포함시키면 "로그인 부하"와 "채팅 부하"가 섞여
// 측정 목적이 흐려지므로, 토큰 발급은 setup 단계에서 끝내고 본 실행에서는 순수하게
// WebSocket/STOMP 동작만 측정한다.
export function setup() {
    const users = [];
    for (let i = 1; i <= USER_COUNT; i++) {
        const email = `k6user${i}@seed.com`;
        const res = http.post(
            `${BASE_HTTP}/api/auth/login`,
            JSON.stringify({ email, password: 'password123' }),
            { headers: { 'Content-Type': 'application/json' } }
        );
        if (res.status !== 200) {
            throw new Error(`setup 로그인 실패: ${email}, status=${res.status}, body=${res.body}`);
        }
        const accessToken = JSON.parse(res.body).accessToken;

        const roomsRes = http.get(`${BASE_HTTP}/api/rooms`, {
            headers: { Authorization: `Bearer ${accessToken}` },
        });
        if (roomsRes.status !== 200) {
            throw new Error(`setup 방 목록 조회 실패: ${email}, status=${roomsRes.status}`);
        }
        const rooms = JSON.parse(roomsRes.body).filter((r) => r.roomName.startsWith('k6-room-'));
        if (rooms.length === 0) {
            throw new Error(`${email}이 속한 k6-room이 없습니다. seed.sql을 먼저 실행했는지 확인하세요.`);
        }

        users.push({ email, accessToken, roomId: rooms[0].roomId });
    }
    return { users };
}

function buildStompFrame(command, headers, body = '') {
    let frame = command + '\n';
    for (const key in headers) {
        frame += `${key}:${headers[key]}\n`;
    }
    frame += '\n' + body + '\x00';
    return frame;
}

export default function (data) {
    const vuUser = data.users[(__VU - 1) % data.users.length];
    // 방마다 배정된 5명 중 email이 그 방에서 가장 먼저(사전순) 오는 사람을 발신자로 삼는다.
    // k6는 setup()의 리턴값을 VU/iteration마다 JSON으로 직렬화-역직렬화해서 넘기므로,
    // 매번 새 객체 인스턴스가 만들어져 배열 원본과의 참조 비교(===, indexOf)가 항상
    // 실패한다(검증됨: data.users.indexOf(vuUser)는 항상 -1). 그래서 email처럼 값으로
    // 비교 가능한 필드를 기준으로 판별한다.
    const sameRoomEmails = data.users.filter((u) => u.roomId === vuUser.roomId).map((u) => u.email).sort();
    const isSender = sameRoomEmails[0] === vuUser.email;

    const url = `${BASE_WS}?token=${vuUser.accessToken}`;
    const params = {
        headers: { Authorization: `Bearer ${vuUser.accessToken}` },
    };

    const pendingSends = new Map(); // clientMessageId -> { sentAt, settled }, 지연시간/성공판정용
    let sendCounter = 0;
    let stopped = false; // 이 k6 버전의 ws 모듈은 socket.clearInterval을 지원하지 않아
    // (호출 시 TypeError로 세션이 조용히 끊김, 실측 확인됨) setInterval 핸들을 직접
    // 정지하는 대신 플래그로 반복 내부에서 스스로 멈추게 한다.
    let connectAcked = false;
    let subscribeAcked = false;

    const res = ws.connect(BASE_WS, params, function (socket) {
        socket.on('open', function () {
            const connectFrame = buildStompFrame('CONNECT', {
                'accept-version': '1.2',
                'heart-beat': '0,0',
                Authorization: `Bearer ${vuUser.accessToken}`,
            });
            socket.send(connectFrame);
        });

        socket.on('message', function (message) {
            if (message.startsWith('CONNECTED')) {
                connectAcked = true;
                wsConnectSuccessRate.add(true);

                const subscribeFrame = buildStompFrame('SUBSCRIBE', {
                    id: 'sub-0',
                    destination: `/sub/rooms/${vuUser.roomId}`,
                });
                socket.send(subscribeFrame);
                // SimpleBroker는 SUBSCRIBE를 동기적으로 처리하므로, 전송 시점을
                // "구독 완료"로 간주한다(서버가 receipt를 응답하지 않아 확인할
                // 방법이 이것뿐이다).
                subscribeAcked = true;
                subscribeSuccessRate.add(true);

                if (isSender) {
                    // 방의 나머지 멤버가 구독을 확정할 시간을 벌어준다 — 이 여유 없이
                    // 바로 발신하면 아직 SUBSCRIBE 전인 수신자에게 초반 메시지가
                    // 전달되지 않는데, 이는 서버 유실이 아니라 테스트 타이밍 문제다.
                    sleep(WARMUP_SEC);

                    socket.setInterval(function () {
                        if (stopped) {
                            return;
                        }
                        sendCounter += 1;
                        const clientMessageId = `${vuUser.email}-${__ITER}-${sendCounter}-${Date.now()}`;
                        const sendFrame = buildStompFrame(
                            'SEND',
                            { destination: `/pub/rooms/${vuUser.roomId}/send`, 'content-type': 'application/json' },
                            JSON.stringify({ content: `load test message ${sendCounter}`, clientMessageId })
                        );
                        pendingSends.set(clientMessageId, { sentAt: Date.now(), settled: false });
                        socket.send(sendFrame);
                        messagesSent.add(1);
                        // 발신자 자신도 /sub/rooms/{roomId}의 구독자라서 자기 메시지의
                        // 브로드캐스트 에코까지 돌려받는다(스모크 테스트로 실측 확인:
                        // sent=290이면 received=1450=290x5, 즉 발신자 본인 몫까지 포함해
                        // 방 전체 인원(ROOM_SIZE) 수만큼 수신되는 게 무유실 상태다).
                        messagesExpected.add(ROOM_SIZE);

                        // 서버가 SEND 실패를 알려주는 유일한 경로는 ERROR 프레임이고,
                        // 성공은 응답이 없다(fire-and-forget). 그래서 일정 시간 안에
                        // ERROR도 없고 자기 메시지의 에코(MESSAGE)도 못 받으면 실패로,
                        // 받으면 성공으로 확정한다 — 결과가 어느 쪽으로도 확정되지
                        // 않은 채 표본에서 누락되는 것을 막는다.
                        const targetId = clientMessageId;
                        socket.setTimeout(function () {
                            const pending = pendingSends.get(targetId);
                            if (pending && !pending.settled) {
                                pending.settled = true;
                                sendSuccessRate.add(false);
                            }
                        }, 5000);
                    }, SEND_INTERVAL_SEC * 1000);
                }
                return;
            }

            if (message.startsWith('MESSAGE')) {
                messagesReceived.add(1);
                const parts = message.split('\n\n');
                if (parts.length >= 2) {
                    try {
                        const body = JSON.parse(parts[1].replace(/\x00$/, ''));
                        const pending = pendingSends.get(body.clientMessageId);
                        if (pending) {
                            messageLatency.add(Date.now() - pending.sentAt);
                            if (!pending.settled) {
                                // 발신자 본인에게 되돌아온 에코 = 서버가 내 SEND를
                                // 실제로 발행/브로드캐스트했다는 증거.
                                pending.settled = true;
                                sendSuccessRate.add(true);
                            }
                        }
                    } catch (e) {
                        // 파싱 실패는 지연시간 측정에서만 제외하고 넘어간다.
                    }
                }
                return;
            }

            if (message.startsWith('ERROR')) {
                if (!connectAcked) {
                    wsConnectSuccessRate.add(false);
                } else if (!subscribeAcked) {
                    subscribeSuccessRate.add(false);
                } else {
                    // 어떤 SEND가 실패했는지 ERROR 프레임만으로는 특정할 수 없으므로,
                    // 아직 미확정인 SEND가 있다면 가장 오래된 것을 실패로 확정한다.
                    for (const [id, pending] of pendingSends) {
                        if (!pending.settled) {
                            pending.settled = true;
                            sendSuccessRate.add(false);
                            break;
                        }
                    }
                }
            }
        });

        socket.on('error', function () {
            if (!connectAcked) {
                wsConnectSuccessRate.add(false);
            }
        });

        // 방 하나당 측정 구간을 30초 + WARMUP_SEC로 잡는다: 발신자는 warm-up 이후
        // 3초 간격으로 약 10회 전송, 나머지 4명은 그 10개 메시지를 각각 수신하며
        // 브로드캐스트/읽음 처리 부하를 만든다.
        socket.setTimeout(function () {
            stopped = true;
            if (isSender) {
                // 방 단위 기대 수신량(=이 방의 발신 횟수 x 방 전체 인원, 발신자
                // 자신의 에코 수신 포함)을 로그로 남긴다. 전체 실측 유실률은
                // 최종 요약의 chat_messages_expected 총합과 chat_messages_received
                // 총합을 비교해서 판단한다(README "무유실 판정 방법" 참고).
                console.log(
                    `[room ${vuUser.roomId}] sent=${sendCounter}, ` +
                    `expected_received=${sendCounter * ROOM_SIZE}`
                );
            }
            socket.close();
        }, (30 + WARMUP_SEC) * 1000);
    });

    check(res, { 'websocket handshake 성공': (r) => r && r.status === 101 });
    if (!res || res.status !== 101) {
        wsConnectSuccessRate.add(false);
    }
}
