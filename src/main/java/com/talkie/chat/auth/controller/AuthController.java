package com.talkie.chat.auth.controller;

import com.talkie.chat.auth.dto.LoginRequest;
import com.talkie.chat.auth.dto.ResendVerificationRequest;
import com.talkie.chat.auth.dto.SignupRequest;
import com.talkie.chat.auth.dto.TokenResponse;
import com.talkie.chat.auth.dto.VerifyEmailRequest;
import com.talkie.chat.auth.service.AuthService;
import com.talkie.chat.auth.utils.CookieUtil;
import com.talkie.chat.global.exception.ApiResponse;
import com.talkie.chat.global.exception.ErrorResponse;
import com.talkie.chat.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "회원가입/로그인/토큰 재발급/로그아웃")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "회원가입", description = "이메일/닉네임 중복이 없으면 계정을 생성하고 인증코드를 이메일로 발송한다. " +
            "이메일 인증 전까지는 로그인할 수 없다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "회원가입 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이메일 또는 닉네임 중복",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @SecurityRequirements
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<UserResponse>> signup(@Valid @RequestBody SignupRequest request) {
        UserResponse signup = authService.signup(request);
        return ResponseEntity.ok(ApiResponse.ok(signup));
    }

    @Operation(summary = "이메일 인증", description = "회원가입 시 발송된 6자리 인증코드를 검증한다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "인증 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "인증코드 불일치 또는 만료",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "가입되지 않은 이메일",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 인증된 이메일",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @SecurityRequirements
    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.email(), request.code());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(summary = "인증코드 재발송", description = "인증코드가 만료됐거나 메일을 받지 못한 경우 재발송한다. 1분 간격 제한이 있다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "재발송 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "가입되지 않은 이메일",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 인증된 이메일",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "재발송 간격 미충족",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @SecurityRequirements
    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request.email());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(summary = "로그인", description = "AccessToken은 응답 바디로, RefreshToken은 HttpOnly 쿠키로 내려준다. " +
            "이메일 미인증 상태면 401을 반환한다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "이메일/비밀번호 불일치 또는 이메일 미인증",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request, HttpServletResponse http) {
        TokenResponse response = authService.login(request);

        CookieUtil.createCookie(http, response.refreshToken());

        return ResponseEntity.ok(ApiResponse.ok(new TokenResponse(response.accessToken(), null)));
    }

    @Operation(summary = "토큰 재발급", description = "쿠키의 RefreshToken을 검증해 새 AccessToken/RefreshToken을 발급한다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "재발급 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "RefreshToken이 없거나 유효하지 않음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @SecurityRequirements
    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<TokenResponse>> reissue(HttpServletRequest request, HttpServletResponse response) {
        Cookie[] cookies = request.getCookies();

        String refreshToken = null;
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("REFRESH_TOKEN".equals(cookie.getName())) {
                    refreshToken = cookie.getValue();
                }
            }
        }

        TokenResponse reissuedResponse = authService.reissue(refreshToken);
        CookieUtil.createCookie(response, reissuedResponse.refreshToken());

        return ResponseEntity.ok(ApiResponse.ok(new TokenResponse(reissuedResponse.accessToken(), null)));
    }

    @Operation(summary = "로그아웃", description = "저장된 RefreshToken을 삭제하고 쿠키를 만료시킨다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "RefreshToken이 없거나 유효하지 않음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @SecurityRequirements
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request, HttpServletResponse response) {
        Cookie[] cookies = request.getCookies();

        String refreshToken = null;
        if (cookies != null) {
            for(Cookie cookie : cookies) {
                if ("REFRESH_TOKEN".equals(cookie.getName())) {
                    refreshToken = cookie.getValue();
                }
            }
        }

        CookieUtil.deleteCookie(response);
        authService.logout(refreshToken);

        return ResponseEntity.ok(ApiResponse.ok());
    }
}
