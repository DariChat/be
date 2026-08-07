# Talkie

실시간 채팅 서비스. WebSocket(STOMP)을 기반으로 1:1 및 그룹 채팅, 읽음 처리, 메시지 커서 페이지네이션을 지원한다.

## 기능

- **인증**: 이메일/비밀번호 회원가입, JWT 기반 로그인 (AccessToken + RefreshToken), 토큰 재발급, 로그아웃
- **프로필**: 내 정보 조회, 닉네임/프로필 이미지 수정, 비밀번호 변경
- **채팅방**: 1:1(DIRECT) / 그룹(GROUP) 채팅방 생성, 내 채팅방 목록 조회(마지막 메시지·안읽은 수 포함), 퇴장(방장 위임 및 마지막 인원 퇴장 시 자동 삭제)
- **메시지**: STOMP를 통한 실시간 송수신, `clientMessageId` 기반 멱등 발행(중복 전송/재전송 방지), 커서 기반 페이지네이션 조회
- **읽음 처리**: 방 구독/구독 해제 시점 기준 읽음 처리, 실시간 안읽은 메시지 수 반영

## 기술 스택

**Backend**
- Java 17, Spring Boot 4.1
- Spring Security (JWT 인증)
- Spring Data JPA, MySQL
- WebSocket + STOMP
- Redis (RefreshToken 저장)
- springdoc-openapi (Swagger UI)

**Infra**
- Docker / Docker Compose (단일 앱 인스턴스)
- GitHub Actions (CI/CD)
- k6 (부하 테스트)

## 아키텍처

```
   Client ───────── app (8080 → 80)
                        │
              ┌─────────┴─────────┐
              │                   │
        ┌─────▼─────┐       ┌─────▼─────┐
        │   MySQL   │       │   Redis   │
        └───────────┘       └───────────┘
```

- REST API는 `/api/**`, WebSocket 핸드셰이크는 `/ws-talkie`로 노출된다.
- STOMP 메시지는 `/pub/**`(클라이언트 발행) / `/sub/**`, `/queue/**`(서버 브로드캐스트)로 라우팅된다.
- 인증은 REST에서 `JwtAuthFilter`, STOMP CONNECT 프레임에서 `StompChannelInterceptor`가 각각 JWT를 검증한다.
- Redis는 RefreshToken 저장소로 사용한다. 단일 인스턴스로 운영하므로 메시지 브로드캐스트는 STOMP로 직접 전송하며 별도의 pub/sub 계층을 두지 않는다(설계 배경은 `DESIGN_NOTES.md` 참고). 이후 인스턴스를 여러 대로 확장할 경우 Redis pub/sub 또는 STOMP 브로커 릴레이 재도입이 필요하다.

## 패키지 구조

```
com.talkie.chat
├── auth      회원가입/로그인/토큰 재발급/로그아웃
├── user      프로필 조회/수정
├── room      채팅방 생성/조회/퇴장, 구독 리스너
├── message   메시지 저장/조회, STOMP 컨트롤러
└── global    공통 설정(Security, WebSocket, Redis, Swagger), 예외 처리, JWT
```

## API

### REST

| Method | URL | 설명 |
|---|---|---|
| POST | `/api/auth/signup` | 회원가입 |
| POST | `/api/auth/login` | 로그인 |
| POST | `/api/auth/reissue` | 토큰 재발급 |
| POST | `/api/auth/logout` | 로그아웃 |
| GET | `/api/users/me` | 내 프로필 조회 |
| PUT | `/api/users/update` | 프로필 수정 |
| PUT | `/api/users/update/password` | 비밀번호 변경 |
| POST | `/api/rooms` | 채팅방 생성 |
| GET | `/api/rooms` | 내 채팅방 목록 조회 |
| DELETE | `/api/rooms/{id}/leave` | 채팅방 퇴장 |
| GET | `/api/rooms/{roomId}/messages` | 메시지 커서 조회 |

응답은 `{ "success": true, "data": ... }` / `{ "success": false, "error": { "code", "message" } }` 형태로 통일되어 있다.

### WebSocket / STOMP

| 구분 | 경로 | 설명 |
|---|---|---|
| 핸드셰이크 | `/ws-talkie` | STOMP 연결 (CONNECT 헤더에 `Authorization: Bearer {accessToken}`) |
| 발행 | `/pub/rooms/{roomId}/send` | 메시지 전송 |
| 구독 | `/sub/rooms/{roomId}` | 방 메시지 수신 |
| 구독 | `/user/queue/errors` | 개인 에러 응답 수신 |

전체 API 스펙은 서버 기동 후 `/swagger-ui/index.html`에서 확인할 수 있다.

## 로컬 실행

### 요구 사항
- JDK 17
- MySQL, Redis (로컬 기동 또는 아래 Docker Compose 사용)

### 프로필
- `local`: 로컬 개발용. `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` 환경변수 필요 (`CORS_ALLOWED_ORIGINS`는 기본값 있음)
- `test`: 테스트 전용. H2 인메모리 DB 사용, 별도 설정 불필요
- `deploy`: 배포용. 모든 값(`DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS` 등)을 환경변수로 필수 주입

```bash
# 테스트 실행 (Redis 필요)
./gradlew test

# 로컬 실행
JWT_SECRET=... DB_USERNAME=... DB_PASSWORD=... SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

### Docker Compose로 전체 스택 실행

```bash
# .env에 MYSQL_ROOT_PASSWORD, MYSQL_DATABASE, DB_USERNAME, DB_PASSWORD, JWT_SECRET, CORS_ALLOWED_ORIGINS 설정 후
docker-compose up -d --build
```

`app` 컨테이너가 80번 포트로 직접 노출된다.

## 부하 테스트

`scripts/k6/chat_load_test.js`로 STOMP 채팅 부하 테스트를 수행할 수 있다.

## 문서

- [DESIGN_NOTES.md](./DESIGN_NOTES.md): 코드만으로 드러나지 않는 설계 결정과 그 배경 (멱등성 처리, 커서 페이지네이션, 비동기 읽음 처리 등)
