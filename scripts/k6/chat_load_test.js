// k6 WebSocket 부하 테스트: 단일 서버 환경에서 채팅방 동시접속자 시뮬레이션
//
// 시나리오: k6/seed.sql로 만든 유저 50명 / 방 10개(방마다 5명)를 사용한다.
// 각 VU(가상유저)는 로그인 후 자신이 속한 방을 구독하고, 방마다 1명(발신자)은
// 주기적으로 메시지를 전송, 나머지 4명은 수신만 하며 대기한다.
// 이렇게 하면 STOMP 연결/구독 유지, 메시지 발행(DB 쓰기), 브로드캐스트 팬아웃,
// 비동기 읽음 처리까지 이 서비스의 핵심 부하 지점을 한 번에 재현할 수 있다.
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
const SEND_INTERVAL_SEC = Number(__ENV.SEND_INTERVAL_SEC || 3); // 발신자가 메시지를 보내는 주기

const messageLatency = new Trend('chat_message_latency_ms', true);
const messagesSent = new Counter('chat_messages_sent');
const messagesReceived = new Counter('chat_messages_received');
const sendErrors = new Rate('chat_send_error_rate');
const wsConnectErrors = new Rate('chat_ws_connect_error_rate');

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
        chat_send_error_rate: ['rate<0.01'],
        chat_ws_connect_error_rate: ['rate<0.01'],
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

    const pendingSends = new Map(); // clientMessageId -> 전송 시각(ms), 지연시간 측정용
    let sendCounter = 0;
    let stopped = false; // 이 k6 버전의 ws 모듈은 socket.clearInterval을 지원하지 않아
    // (호출 시 TypeError로 세션이 조용히 끊김, 실측 확인됨) setInterval 핸들을 직접
    // 정지하는 대신 플래그로 반복 내부에서 스스로 멈추게 한다.

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
                const subscribeFrame = buildStompFrame('SUBSCRIBE', {
                    id: 'sub-0',
                    destination: `/sub/rooms/${vuUser.roomId}`,
                });
                socket.send(subscribeFrame);

                if (isSender) {
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
                        pendingSends.set(clientMessageId, Date.now());
                        socket.send(sendFrame);
                        messagesSent.add(1);
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
                        const sentAt = pendingSends.get(body.clientMessageId);
                        if (sentAt) {
                            messageLatency.add(Date.now() - sentAt);
                            pendingSends.delete(body.clientMessageId);
                        }
                    } catch (e) {
                        // 파싱 실패는 지연시간 측정에서만 제외하고 넘어간다.
                    }
                }
                return;
            }

            if (message.startsWith('ERROR')) {
                sendErrors.add(1);
            }
        });

        socket.on('error', function () {
            wsConnectErrors.add(1);
        });

        // 방 하나당 측정 구간을 30초로 잡는다: 발신자는 3초 간격으로 총 약 10회 전송,
        // 나머지 4명은 그 10개 메시지를 각각 수신하며 브로드캐스트/읽음 처리 부하를 만든다.
        socket.setTimeout(function () {
            stopped = true;
            socket.close();
        }, 30000);
    });

    check(res, { 'websocket handshake 성공': (r) => r && r.status === 101 });
}
