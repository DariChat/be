package com.talkie.chat.auth.service;

import com.talkie.chat.auth.dto.LoginRequest;
import com.talkie.chat.auth.dto.SignupRequest;
import com.talkie.chat.auth.dto.TokenResponse;
import com.talkie.chat.auth.exception.AuthErrorCode;
import com.talkie.chat.auth.repository.RefreshTokenRepository;
import com.talkie.chat.global.exception.BusinessException;
import com.talkie.chat.global.jwt.JwtProvider;
import com.talkie.chat.user.dto.UserResponse;
import com.talkie.chat.user.entity.User;
import com.talkie.chat.user.exception.UserErrorCode;
import com.talkie.chat.user.repository.UserRepository;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.OngoingStubbing;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static java.util.List.of;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

// 힌트: 필드 mock 선언 후, 각 @Nested 안에서 given(when(...).thenReturn(...))으로
// 시나리오를 세팅하고 when(실제 호출) -> then(assertThat/assertThrows)으로 검증하세요.
// AuthErrorCode의 각 에러코드(DUPLICATE_EMAIL, DUPLICATE_NICKNAME,
// DUPLICATE_EMAIL_OR_NICKNAME, LOGIN_FAILED, INVALID_TOKEN)를 참고해 예외 케이스를 채우세요.
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtProvider jwtProvider;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, refreshTokenRepository, passwordEncoder, jwtProvider);
    }

    @Nested
    @DisplayName("회원가입")
    class Signup {

        @Test
        @DisplayName("이메일이 중복되면 예외를 던진다")
        void signup_duplicateEmail() {
            // TODO: given-when-then
            SignupRequest signupRequest =
                    new SignupRequest("KimMinsu", "test01@gmail.com", "password12", "test01");
            when(userRepository.existsByEmail(signupRequest.email())).thenReturn(true);

            BusinessException exception = assertThrows(BusinessException.class, () -> authService.signup(signupRequest));
            assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.DUPLICATE_EMAIL);
        }

        @Test
        @DisplayName("닉네임이 중복되면 예외를 던진다")
        void signup_duplicateNickname() {
            // TODO: given-when-then
            SignupRequest signupRequest =
                    new SignupRequest("KimMinsu", "test01@gmail.com", "password12", "test01");
            when(userRepository.existsByEmail(signupRequest.email())).thenReturn(false);
            when(userRepository.existsByNickname(signupRequest.nickname())).thenReturn(true);

            BusinessException exception = assertThrows(BusinessException.class, () -> authService.signup(signupRequest));
            assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.DUPLICATE_NICKNAME);
        }

        @Test
        @DisplayName("사전 체크를 통과했지만 저장 시점에 DataIntegrityViolationException이 발생하면 " +
                "DUPLICATE_EMAIL_OR_NICKNAME으로 변환한다 (레이스 컨디션 재현)")
        void signup_raceConditionOnSave() {
            // TODO: given-when-then
            SignupRequest signupRequest =
                    new SignupRequest("KimMinsu", "test01@gmail.com", "password12", "test01");
            when(userRepository.existsByEmail(signupRequest.email())).thenReturn(false);
            when(userRepository.existsByNickname(signupRequest.nickname())).thenReturn(false);
            when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

            BusinessException exception = assertThrows(BusinessException.class, () -> authService.signup(signupRequest));
            assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.DUPLICATE_EMAIL_OR_NICKNAME);
        }

        @Test
        @DisplayName("정상 가입 시 비밀번호가 인코딩되어 저장된다")
        void signup_success() {
            // TODO: given-when-then
            SignupRequest signupRequest =
                    new SignupRequest("KimMinsu", "test01@gmail.com", "password12", "test01");
            when(userRepository.existsByEmail(signupRequest.email())).thenReturn(false);
            when(userRepository.existsByNickname(signupRequest.nickname())).thenReturn(false);
            when(passwordEncoder.encode(signupRequest.password())).thenReturn("ENCODED");

            User savedUser = new User("KimMinsu", "ENCODED", "test01@gmail.com", "test01", null);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            UserResponse response = authService.signup(signupRequest);
            assertThat(response.email()).isEqualTo("test01@gmail.com");
            assertThat(response.nickname()).isEqualTo("test01");
            verify(userRepository).save(argThat(user -> user.getPassword().equals("ENCODED")));
        }
    }

    @Nested
    @DisplayName("로그인")
    class Login {

        @Test
        @DisplayName("존재하지 않는 이메일이면 LOGIN_FAILED (이메일/비밀번호 오류와 동일 메시지인지도 확인)")
        void login_emailNotFound() {
            // TODO: given-when-then
            LoginRequest request = new LoginRequest("test01@gmail.com", "password01");

            when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());

            BusinessException exception = assertThrows(BusinessException.class, () -> authService.login(request));
            assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.LOGIN_FAILED);
        }

        @Test
        @DisplayName("비밀번호가 일치하지 않으면 LOGIN_FAILED")
        void login_wrongPassword() {
            // TODO: given-when-then
            LoginRequest request = new LoginRequest("test01@gmail.com", "password01");
            User user = new User("KimMinsu", "ENCODED_PASSWORD", "test01@gmail.com", "test01", null);

            when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(request.password(), user.getPassword())).thenReturn(false);

            BusinessException exception = assertThrows(BusinessException.class, () -> authService.login(request));
            assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.LOGIN_FAILED);

        }

        @Test
        @DisplayName("정상 로그인 시 RefreshToken이 저장되고 lastActiveAt이 갱신된다")
        void login_success() {
            // TODO: given-when-then
            LoginRequest request = new LoginRequest("test01@gmail.com", "password01");
            User user = new User("KimMinsu", "ENCODED_PASSWORD", "test01@gmail.com", "test01", null);

            when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(request.password(), user.getPassword())).thenReturn(true);
            when(jwtProvider.generateAccessToken(user.getId())).thenReturn("ACCESS_TOKEN");
            when(jwtProvider.generateRefreshToken(user.getId())).thenReturn("REFRESH_TOKEN");
            when(jwtProvider.getRefreshTokenExpiration()).thenReturn(100L);

            TokenResponse response = authService.login(request);

            assertThat(response.accessToken()).isEqualTo("ACCESS_TOKEN");
            assertThat(response.refreshToken()).isEqualTo("REFRESH_TOKEN");
            verify(refreshTokenRepository).save(user.getId(), "REFRESH_TOKEN", 100L);
        }
    }

    @Nested
    @DisplayName("토큰 재발급")
    class Reissue {

        @Test
        @DisplayName("유효하지 않은 토큰이면 INVALID_TOKEN")
        void reissue_invalidToken() {
            // TODO: given-when-then
            String token = "REFRESH_TOKEN";

            when(jwtProvider.isValid(token)).thenReturn(false);

            BusinessException exception = assertThrows(BusinessException.class, () -> authService.reissue(token));
            assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_TOKEN);
        }

        @Test
        @DisplayName("RefreshToken이 아닌 토큰(access 타입)이 오면 INVALID_TOKEN")
        void reissue_wrongTokenType() {
            // TODO: given-when-then
            String token = "REFRESH_TOKEN";

            when(jwtProvider.isValid(token)).thenReturn(true);
            when(jwtProvider.isRefreshToken(token)).thenReturn(false);

            BusinessException exception = assertThrows(BusinessException.class, () -> authService.reissue(token));
            assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_TOKEN);
        }

        @Test
        @DisplayName("Redis에 저장된 토큰과 요청 토큰이 다르면 INVALID_TOKEN (이미 재발급되어 무효화된 토큰 재사용)")
        void reissue_tokenMismatch() {
            // TODO: given-when-then
            String token = "OLD_REFRESH_TOKEN";
            Long userId = 1L;

            when(jwtProvider.isValid(token)).thenReturn(true);
            when(jwtProvider.isRefreshToken(token)).thenReturn(true);
            when(jwtProvider.extractUserId(token)).thenReturn(userId);
            when(refreshTokenRepository.find(userId)).thenReturn("NEW_REFRESH_TOKEN");

            BusinessException exception = assertThrows(BusinessException.class, () -> authService.reissue(token));
            assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_TOKEN);
        }

        @Test
        @DisplayName("정상 재발급 시 새 토큰 쌍이 발급되고 Redis가 갱신된다")
        void reissue_success() {
            // TODO: given-when-then
            String token = "OLD_REFRESH_TOKEN";
            String generatedAccessToken = "ACCESS_TOKEN";
            String generatedRefreshToken = "REFRESH_TOKEN";
            Long userId = 1L;
            long expired = 100L;

            when(jwtProvider.isValid(token)).thenReturn(true);
            when(jwtProvider.isRefreshToken(token)).thenReturn(true);
            when(jwtProvider.extractUserId(token)).thenReturn(userId);
            when(refreshTokenRepository.find(userId)).thenReturn("OLD_REFRESH_TOKEN");
            when(jwtProvider.generateAccessToken(userId)).thenReturn(generatedAccessToken);
            when(jwtProvider.generateRefreshToken(userId)).thenReturn(generatedRefreshToken);
            when(jwtProvider.getRefreshTokenExpiration()).thenReturn(expired);
            TokenResponse response = authService.reissue(token);

            assertThat(response.accessToken()).isEqualTo(generatedAccessToken);
            assertThat(response.refreshToken()).isEqualTo(generatedRefreshToken);
            verify(refreshTokenRepository).save(userId, generatedRefreshToken, expired);
        }
    }

    @Nested
    @DisplayName("로그아웃")
    class Logout {

        @Test
        @DisplayName("유효하지 않은 토큰이면 INVALID_TOKEN")
        void logout_invalidToken() {
            // TODO: given-when-then
            String token = "TOKEN";

            when(jwtProvider.isValid(token)).thenReturn(false);

            BusinessException exception = assertThrows(BusinessException.class, () -> authService.logout(token));
            assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_TOKEN);
        }

        @Test
        @DisplayName("정상 로그아웃 시 Redis에서 RefreshToken이 삭제된다")
        void logout_success() {
            // TODO: given-when-then
            String token = "TOKEN";
            Long userId = 1L;

            when(jwtProvider.isValid(token)).thenReturn(true);
            when(jwtProvider.isRefreshToken(token)).thenReturn(true);
            when(jwtProvider.extractUserId(token)).thenReturn(userId);

            authService.logout(token);
            verify(refreshTokenRepository).delete(userId);
        }
    }
}
