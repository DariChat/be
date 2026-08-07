package com.talkie.chat.websocket;

import com.talkie.chat.auth.dto.LoginRequest;
import com.talkie.chat.auth.dto.SignupRequest;
import com.talkie.chat.auth.service.AuthService;
import com.talkie.chat.global.exception.CommonErrorCode;
import com.talkie.chat.global.exception.ErrorResponse;
import com.talkie.chat.global.jwt.JwtProvider;
import com.talkie.chat.message.dto.ChatMessageRequest;
import com.talkie.chat.message.dto.MessageResponse;
import com.talkie.chat.message.exception.MessageErrorCode;
import com.talkie.chat.room.dto.RoomResponse;
import com.talkie.chat.room.enums.RoomType;
import com.talkie.chat.room.service.RoomService;
import com.talkie.chat.user.dto.UserResponse;
import com.talkie.chat.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

// 힌트: WebSocketStompClient는 CONNECT 시 STOMP native header로 Authorization을 실어 보낼 수
// 있습니다(StompHeaders.add("Authorization", "Bearer " + token)). 로그인/방 생성은
// TestRestTemplate이나 직접 서비스 빈을 주입받아 미리 세팅해두는 게 편합니다.
// 메시지 수신은 비동기라 BlockingQueue.poll(timeout)로 동기적으로 기다리는 패턴을 씁니다.
// StompSessionHandlerAdapter를 상속해 handleFrame에서 큐에 담는 커스텀 핸들러를 만들어
// connect(headers, handler)에 넘기면 CONNECTED 이후 세션을 받을 수 있습니다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ChatWebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    private WebSocketStompClient stompClient;

    @Autowired
    private AuthService authService;
    @Autowired
    private RoomService roomService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtProvider jwtProvider;

    private Long userId;
    private Long userId2;
    private String accessToken;
    private String accessToken2;
    private Long roomId;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());

        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);

        authService.signup(new SignupRequest("Test", "test01-" + suffix + "@gmail.com", "password01", "Test01-" + suffix));
        accessToken = authService.login(new LoginRequest("test01-" + suffix + "@gmail.com", "password01")).accessToken();
        userId = jwtProvider.extractUserId(accessToken);

        authService.signup(new SignupRequest("Kim", "test02-" + suffix + "@gmail.com", "password01", "Test02-" + suffix));
        accessToken2 = authService.login(new LoginRequest("test02-" + suffix + "@gmail.com", "password01")).accessToken();
        userId2 = jwtProvider.extractUserId(accessToken2);

        roomId = roomService.createRoom(userId, "test room", RoomType.GROUP, List.of(userId2)).roomId();
    }

    @AfterEach
    void tearDown() {
        stompClient.stop();
    }

    private String wsUrl() {
        return "ws://localhost:" + port + "/ws-talkie";
    }

    /**
     * STOMP 프레임을 BlockingQueue에 담아 동기적으로 기다릴 수 있게 해주는 핸들러.
     * connect() 시 넘기면 CONNECTED 시점에 session이 채워지고,
     * subscribe() 시 넘기면 해당 destination의 메시지가 payload 큐에 쌓인다.
     */
    private static class QueueingStompFrameHandler implements StompFrameHandler {
        private final BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
        private final Class<?> payloadType;

        QueueingStompFrameHandler(Class<?> payloadType) {
            this.payloadType = payloadType;
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return payloadType;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            queue.add(payload);
        }

        Object awaitPayload(long timeout, TimeUnit unit) throws InterruptedException {
            return queue.poll(timeout, unit);
        }
    }

    private static class RecordingStompSessionHandler extends StompSessionHandlerAdapter {
        private final BlockingQueue<StompSession> sessionQueue = new LinkedBlockingQueue<>();
        private final BlockingQueue<Throwable> errorQueue = new LinkedBlockingQueue<>();

        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            sessionQueue.add(session);
        }

        @Override
        public void handleException(StompSession session, StompCommand command, StompHeaders headers,
                                     byte[] payload, Throwable exception) {
            errorQueue.add(exception);
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            errorQueue.add(exception);
        }

        StompSession awaitSession(long timeout, TimeUnit unit) throws InterruptedException {
            return sessionQueue.poll(timeout, unit);
        }
    }

    @Nested
    @DisplayName("CONNECT")
    class Connect {

        @Test
        @DisplayName("유효한 JWT로 연결하면 성공한다")
        void connect_withValidToken_succeeds() throws Exception {
            StompHeaders connectHeaders = new StompHeaders();
            connectHeaders.add("Authorization", "Bearer " + accessToken);
            RecordingStompSessionHandler handler = new RecordingStompSessionHandler();

            stompClient.connectAsync(wsUrl(), (WebSocketHttpHeaders) null, connectHeaders, handler);
            StompSession session = handler.awaitSession(3, TimeUnit.SECONDS);

            // TODO: then - session이 null이 아니고 session.isConnected()가 true인지
            assertThat(session).isNotNull();
            assertThat(session.isConnected()).isTrue();
        }

        @Test
        @DisplayName("유효하지 않은 토큰으로 연결하면 거부된다")
        void connect_withInvalidToken_fails() throws Exception {
            StompHeaders connectHeaders = new StompHeaders();
            connectHeaders.add("Authorization", "Bearer invalid-token");
            RecordingStompSessionHandler handler = new RecordingStompSessionHandler();

            stompClient.connectAsync(wsUrl(), (WebSocketHttpHeaders) null, connectHeaders, handler);

            // TODO: then
            // - handler.awaitSession(...)이 null(타임아웃)이거나
            // - handler.errorQueue 쪽에 예외가 쌓였는지 (StompChannelInterceptor가
            //   CONNECT 단계에서 BusinessException을 던지는 경로를 검증)
            StompSession session = handler.awaitSession(3, TimeUnit.SECONDS);
            assertThat(session).isNull();
        }
    }

    @Nested
    @DisplayName("SUBSCRIBE + SEND")
    class SubscribeAndSend {

        // TODO: 두 세션(발신자용 session, 구독자로 쓸 두 번째 유저의 session)이 필요한
        // 케이스가 많으니, 공용 헬퍼로 connect(token) -> StompSession 만드는 메서드를
        // 이 Nested 클래스 안에 만들어두면 편함.
        private StompSession connect(String token) throws Exception {
            StompHeaders headers = new StompHeaders();
            headers.add("Authorization", "Bearer " + token);
            RecordingStompSessionHandler handler = new RecordingStompSessionHandler();
            stompClient.connectAsync(wsUrl(), (WebSocketHttpHeaders) null, headers, handler);
            return handler.awaitSession(3, TimeUnit.SECONDS);
        }

        @Test
        @DisplayName("발신자가 보낸 메시지가 구독자에게 브로드캐스트된다")
        void send_broadcastsToSubscriber() throws Exception {
            // TODO: given
            // - StompSession session = connect(accessToken)으로 연결
            // - QueueingStompFrameHandler<MessageResponse> subHandler 준비 후
            //   session.subscribe("/sub/rooms/" + roomId, subHandler)
            // - ChatMessageRequest request = new ChatMessageRequest("hello", "client-msg-1")
            StompSession session = connect(accessToken);
            QueueingStompFrameHandler subHandler = new QueueingStompFrameHandler(MessageResponse.class);
            session.subscribe("/sub/rooms/" + roomId, subHandler);
            // TODO: when
            // session.send("/pub/rooms/" + roomId + "/send", request)
            session.send("/pub/rooms/" + roomId + "/send", new ChatMessageRequest("hello", "client-msg-1"));

            // TODO: then
            // Object payload = subHandler.awaitPayload(3, TimeUnit.SECONDS);
            // payload가 MessageResponse이고 content()가 "hello"인지
            Object payload = subHandler.awaitPayload(3, TimeUnit.SECONDS);
            assertThat(payload).isInstanceOf(MessageResponse.class);
            assertThat(((MessageResponse) payload).content()).isEqualTo("hello");
        }

        @Test
        @DisplayName("방 멤버가 아닌 유저가 메시지를 보내면 /user/queue/errors로 에러를 받는다")
        void send_byNonMember_receivesError() throws Exception {
            // TODO: given
            // - 방에 속하지 않은 새 유저를 signup+login으로 만들고 그 토큰으로 connect
            // - session.subscribe("/user/queue/errors", errorHandler)
            String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
            authService.signup(new SignupRequest("Outsider", "outsider-" + suffix + "@gmail.com", "password01", "Out-" + suffix));
            String outsiderToken = authService.login(new LoginRequest("outsider-" + suffix + "@gmail.com", "password01")).accessToken();

            StompSession session = connect(outsiderToken);
            QueueingStompFrameHandler errorHandler = new QueueingStompFrameHandler(ErrorResponse.class);
            session.subscribe("/user/queue/errors", errorHandler);
            // TODO: when
            // session.send("/pub/rooms/" + roomId + "/send", new ChatMessageRequest("hi", "client-msg-x"))
            session.send("/pub/rooms/" + roomId + "/send", new ChatMessageRequest("hi", "client-msg-x"));

            // TODO: then
            // ErrorResponse error = (ErrorResponse) errorHandler.awaitPayload(3, TimeUnit.SECONDS);
            // error.code()가 MessageErrorCode.NOT_ROOM_MEMBER의 code와 일치하는지
            ErrorResponse error = (ErrorResponse) errorHandler.awaitPayload(3, TimeUnit.SECONDS);
            assertThat(error).isNotNull();
            assertThat(error.error().code()).isEqualTo(MessageErrorCode.NOT_ROOM_MEMBER.getCode());
        }

        @Test
        @DisplayName("빈 content로 메시지를 보내면 검증 실패 에러를 받는다")
        void send_blankContent_receivesValidationError() throws Exception {
            // TODO: given - session connect + "/user/queue/errors" 구독
            StompSession session = connect(accessToken);
            QueueingStompFrameHandler errorHandler = new QueueingStompFrameHandler(ErrorResponse.class);
            session.subscribe("/user/queue/errors", errorHandler);
            // TODO: when
            // session.send("/pub/rooms/" + roomId + "/send", new ChatMessageRequest("", "client-msg-y"))
            session.send("/pub/rooms/" + roomId + "/send", new ChatMessageRequest("", "client-msg-y"));

            // TODO: then - errorHandler로 검증 실패 ErrorResponse가 도착하는지
            ErrorResponse error = (ErrorResponse) errorHandler.awaitPayload(3, TimeUnit.SECONDS);
            assertThat(error).isNotNull();
            assertThat(error.error().code()).isEqualTo(CommonErrorCode.INVALID_INPUT.getCode());
        }

        @Test
        @DisplayName("같은 clientMessageId로 두 번 보내면 두 번째는 재저장/재브로드캐스트되지 않는다 (멱등성)")
        void send_duplicateClientMessageId_isIdempotent() throws Exception {
            // TODO: given - session connect + "/sub/rooms/{roomId}" 구독 + 동일한 clientMessageId
            StompSession session = connect(accessToken);
            QueueingStompFrameHandler subHandler = new QueueingStompFrameHandler(MessageResponse.class);
            session.subscribe("/sub/rooms/" + roomId, subHandler);
            ChatMessageRequest request = new ChatMessageRequest("hello", "dup-client-id");
            // TODO: when - 같은 ChatMessageRequest를 두 번 SEND
            session.send("/pub/rooms/" + roomId + "/send", request);
            MessageResponse first = (MessageResponse) subHandler.awaitPayload(3, TimeUnit.SECONDS);
            assertThat(first).isNotNull();

            session.send("/pub/rooms/" + roomId + "/send", request);
            // TODO: then
            // - 첫 번째 SEND에 대해서만 구독 큐에 브로드캐스트가 오는지 확인
            //   (두 번째 payload를 짧은 timeout으로 poll해서 null인지로 판단하거나,
            //    MessageResponse.id()가 첫 번째와 동일한지로 "재사용됐다"를 검증)
            Object second = subHandler.awaitPayload(1, TimeUnit.SECONDS);
            assertThat(second).isNull();
        }

        @Test
        @DisplayName("다른 사람의 clientMessageId를 재사용하려 하면 거부된다")
        void send_reuseOtherUsersClientMessageId_isRejected() throws Exception {
            // TODO: given
            // - userA(session A)가 clientMessageId="shared-id"로 먼저 SEND해서 메시지 저장
            // - userB(방 멤버, session B)가 "/user/queue/errors" 구독 후 같은 clientMessageId로 SEND
            StompSession sessionA = connect(accessToken);
            sessionA.send("/pub/rooms/" + roomId + "/send", new ChatMessageRequest("first", "shared-id"));
            Thread.sleep(200); // 저장 완료를 보장하기 위한 최소 대기 (또는 별도 확인 수단이 있다면 그걸로 대체)

            StompSession sessionB = connect(accessToken2);
            QueueingStompFrameHandler errorHandler = new QueueingStompFrameHandler(ErrorResponse.class);
            sessionB.subscribe("/user/queue/errors", errorHandler);

            sessionB.send("/pub/rooms/" + roomId + "/send", new ChatMessageRequest("second", "shared-id"));

            // TODO: then
            // errorHandler로 MessageErrorCode.CLIENT_MESSAGE_ID_CONFLICT에 해당하는
            // ErrorResponse가 도착하는지
            ErrorResponse error = (ErrorResponse) errorHandler.awaitPayload(3, TimeUnit.SECONDS);
            assertThat(error).isNotNull();
            assertThat(error.error().code()).isEqualTo(MessageErrorCode.CLIENT_MESSAGE_ID_CONFLICT.getCode());
        }
    }
}
