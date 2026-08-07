package com.talkie.chat.global.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

// 힌트: JwtProvider 생성자는 (secret, accessTokenExpirationMs, refreshTokenExpirationMs)를 받습니다.
// secret은 HMAC-SHA 서명이라 최소 32바이트(256비트) 이상이어야 WeakKeyException이 안 납니다.
// 만료 테스트는 accessTokenExpiration을 아주 짧게(예: 1ms) 준 별도 인스턴스를 만들고
// 토큰 발급 직후 몇 ms만 대기(Thread.sleep)한 뒤 검증하면 느린 sleep 없이 재현 가능합니다.
class JwtProviderTest {

    private static final String TEST_SECRET = "test-secret-key-for-jwt-please-use-32-bytes-min";

    private JwtProvider jwtProvider;

    private static final long ACCESS_EXPIRATION = 3_600_000L;
    private static final long REFRESH_EXPIRATION = 604_800_000L;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(TEST_SECRET, ACCESS_EXPIRATION, REFRESH_EXPIRATION);
    }

    @Nested
    @DisplayName("토큰 생성")
    class GenerateToken {

        @Test
        @DisplayName("AccessToken에는 subject(userId)와 type=access 클레임이 담긴다")
        void generateAccessToken_hasCorrectClaims() {
            Long userId = 1L;

            String token = jwtProvider.generateAccessToken(userId);

            // TODO: then
            // - jwtProvider.extractUserId(token)이 userId와 일치하는지
            assertThat(jwtProvider.extractUserId(token)).isEqualTo(userId);
            // - jwtProvider.isAccessToken(token)이 true인지
            assertThat(jwtProvider.isAccessToken(token)).isTrue();
            // - jwtProvider.isRefreshToken(token)이 false인지
            assertThat(jwtProvider.isRefreshToken(token)).isFalse();
        }

        @Test
        @DisplayName("RefreshToken에는 subject(userId)와 type=refresh 클레임이 담긴다")
        void generateRefreshToken_hasCorrectClaims() {
            Long userId = 1L;

            String token = jwtProvider.generateRefreshToken(userId);

            // TODO: then
            // - jwtProvider.extractUserId(token)이 userId와 일치하는지
            assertThat(jwtProvider.extractUserId(token)).isEqualTo(userId);
            // - jwtProvider.isRefreshToken(token)이 true인지
            assertThat(jwtProvider.isRefreshToken(token)).isTrue();
            // - jwtProvider.isAccessToken(token)이 false인지
            assertThat(jwtProvider.isAccessToken(token)).isFalse();
        }
    }

    @Nested
    @DisplayName("토큰 검증 (extractValidateUserId)")
    class ExtractValidateUserId {

        @Test
        @DisplayName("access 토큰을 access 타입으로 검증하면 userId를 반환한다")
        void extractValidateUserId_validAccessToken() {
            Long userId = 1L;
            String token = jwtProvider.generateAccessToken(userId);

            Optional<Long> result = jwtProvider.extractValidateUserId(token, "access");

            // TODO: then - result가 present이고 값이 userId와 일치하는지
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(userId);
        }

        @Test
        @DisplayName("refresh 토큰을 access 타입으로 검증하면 empty를 반환한다 (type 불일치)")
        void extractValidateUserId_typeMismatch() {
            Long userId = 1L;
            String token = jwtProvider.generateRefreshToken(userId);

            Optional<Long> result = jwtProvider.extractValidateUserId(token, "access");

            // TODO: then - result가 empty인지
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("만료된 토큰은 empty를 반환한다")
        void extractValidateUserId_expiredToken() throws InterruptedException {
            Long userId = 1L;
            // 만료시간이 1ms인 별도 인스턴스로 토큰 발급 후 살짝 대기시켜 만료를 재현
            JwtProvider shortLivedProvider = new JwtProvider(TEST_SECRET, 1L, REFRESH_EXPIRATION);
            String token = shortLivedProvider.generateAccessToken(userId);
            Thread.sleep(10);

            Optional<Long> result = shortLivedProvider.extractValidateUserId(token, "access");

            // TODO: then - result가 empty인지
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("변조된(서명이 깨진) 토큰은 empty를 반환한다")
        void extractValidateUserId_tamperedToken() {
            Long userId = 1L;
            String token = jwtProvider.generateAccessToken(userId);
            // TODO: given - token 문자열의 마지막 몇 글자를 바꿔서 서명을 깨뜨리기
            // 예: String tamperedToken = token.substring(0, token.length() - 1) + "x";
            String tamperedToken = token.substring(0, token.length() - 1) + "x";
            // TODO: when
            Optional<Long> result = jwtProvider.extractValidateUserId(tamperedToken, "access");

            // TODO: then - result가 empty인지
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("다른 secret으로 서명된 토큰은 empty를 반환한다")
        void extractValidateUserId_differentSecret() {
            Long userId = 1L;
            JwtProvider otherProvider = new JwtProvider(
                    "different-secret-key-for-jwt-please-use-32-bytes-min", ACCESS_EXPIRATION, REFRESH_EXPIRATION);
            String token = otherProvider.generateAccessToken(userId);

            Optional<Long> result = jwtProvider.extractValidateUserId(token, "access");

            // TODO: then - result가 empty인지
            assertThat(result).isEmpty();
        }
    }
}
