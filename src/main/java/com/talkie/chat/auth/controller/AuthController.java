package com.talkie.chat.auth.controller;

import com.talkie.chat.auth.dto.LoginRequest;
import com.talkie.chat.auth.dto.SignupRequest;
import com.talkie.chat.auth.dto.TokenResponse;
import com.talkie.chat.auth.service.AuthService;
import com.talkie.chat.auth.utils.CookieUtil;
import com.talkie.chat.global.exception.ErrorResponse;
import com.talkie.chat.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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

    @Operation(summary = "회원가입", description = "이메일/닉네임 중복이 없으면 계정을 생성한다.")
    @ApiResponse(responseCode = "200", description = "회원가입 성공")
    @ApiResponse(responseCode = "409", description = "이메일 또는 닉네임 중복",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @SecurityRequirements
    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest request) {
        UserResponse signup = authService.signup(request);
        return ResponseEntity.ok(signup);
    }

    @Operation(summary = "로그인", description = "AccessToken은 응답 바디로, RefreshToken은 HttpOnly 쿠키로 내려준다.")
    @ApiResponse(responseCode = "200", description = "로그인 성공")
    @ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse http) {
        TokenResponse response = authService.login(request);

        CookieUtil.createCookie(http, response.refreshToken());

        return ResponseEntity.ok(new TokenResponse(response.accessToken(), null));
    }

    @Operation(summary = "토큰 재발급", description = "쿠키의 RefreshToken을 검증해 새 AccessToken/RefreshToken을 발급한다.")
    @ApiResponse(responseCode = "200", description = "재발급 성공")
    @ApiResponse(responseCode = "401", description = "RefreshToken이 없거나 유효하지 않음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @SecurityRequirements
    @PostMapping("/reissue")
    public ResponseEntity<TokenResponse> reissue(HttpServletRequest request, HttpServletResponse response) {
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

        return ResponseEntity.ok(new TokenResponse(reissuedResponse.accessToken(), null));
    }

    @Operation(summary = "로그아웃", description = "저장된 RefreshToken을 삭제하고 쿠키를 만료시킨다.")
    @ApiResponse(responseCode = "204", description = "로그아웃 성공")
    @ApiResponse(responseCode = "401", description = "RefreshToken이 없거나 유효하지 않음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @SecurityRequirements
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
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

        return ResponseEntity.noContent().build();
    }
}
