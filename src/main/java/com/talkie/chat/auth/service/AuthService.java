package com.talkie.chat.auth.service;

import com.talkie.chat.auth.dto.LoginRequest;
import com.talkie.chat.auth.dto.SignupRequest;
import com.talkie.chat.auth.dto.TokenResponse;
import com.talkie.chat.auth.exception.AuthErrorCode;
import com.talkie.chat.auth.repository.EmailVerificationRepository;
import com.talkie.chat.auth.repository.RefreshTokenRepository;
import com.talkie.chat.global.exception.BusinessException;
import com.talkie.chat.global.jwt.JwtProvider;
import com.talkie.chat.user.dto.UserResponse;
import com.talkie.chat.user.entity.User;
import com.talkie.chat.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final long VERIFICATION_CODE_EXPIRATION_MS = 5 * 60 * 1000L;
    private static final long VERIFICATION_RESEND_COOLDOWN_MS = 60 * 1000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final EmailSender emailSender;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Transactional
    public UserResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(AuthErrorCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(AuthErrorCode.DUPLICATE_NICKNAME);
        }

        try {
            User user = new User(
                    request.name(),
                    passwordEncoder.encode(request.password()),
                    request.email(),
                    request.nickname(),
                    null,
                    request.preferredLanguage());
            UserResponse response = UserResponse.from(userRepository.save(user));

            sendVerificationCode(request.email());
            return response;
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(AuthErrorCode.DUPLICATE_EMAIL_OR_NICKNAME, e);
        }
    }

    @Transactional
    public void verifyEmail(String email, String code) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.VERIFICATION_EMAIL_NOT_FOUND));

        if (user.isEmailVerified()) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_VERIFIED);
        }

        String savedCode = emailVerificationRepository.findCode(email);
        if (savedCode == null) {
            throw new BusinessException(AuthErrorCode.VERIFICATION_CODE_NOT_FOUND);
        }
        if (!savedCode.equals(code)) {
            throw new BusinessException(AuthErrorCode.VERIFICATION_CODE_MISMATCH);
        }

        user.verifyEmail();
        emailVerificationRepository.deleteCode(email);
    }

    public void resendVerification(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.VERIFICATION_EMAIL_NOT_FOUND));

        if (user.isEmailVerified()) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_VERIFIED);
        }
        if (!emailVerificationRepository.tryMarkCooldown(email, VERIFICATION_RESEND_COOLDOWN_MS)) {
            throw new BusinessException(AuthErrorCode.VERIFICATION_RESEND_TOO_SOON);
        }

        sendVerificationCode(email);
    }

    private void sendVerificationCode(String email) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        emailVerificationRepository.saveCode(email, code, VERIFICATION_CODE_EXPIRATION_MS);
        emailSender.sendVerificationCode(email, code);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.LOGIN_FAILED));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(AuthErrorCode.LOGIN_FAILED);
        }
        if (!user.isEmailVerified()) {
            throw new BusinessException(AuthErrorCode.EMAIL_NOT_VERIFIED);
        }

        user.updateLastActiveAt();
        String accessToken = jwtProvider.generateAccessToken(user.getId());
        String refreshToken = jwtProvider.generateRefreshToken(user.getId());

        refreshTokenRepository.save(user.getId(), refreshToken, jwtProvider.getRefreshTokenExpiration());

        return new TokenResponse(accessToken, refreshToken);
    }

    public TokenResponse reissue(String refreshToken) {
        if (!jwtProvider.isValid(refreshToken) || !jwtProvider.isRefreshToken(refreshToken)) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }

        Long extractUserId = jwtProvider.extractUserId(refreshToken);

        String token = refreshTokenRepository.find(extractUserId);
        if (!refreshToken.equals(token)) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }

        String generatedAccessToken = jwtProvider.generateAccessToken(extractUserId);
        String generatedRefreshToken = jwtProvider.generateRefreshToken(extractUserId);

        refreshTokenRepository.save(extractUserId, generatedRefreshToken, jwtProvider.getRefreshTokenExpiration());
        return new TokenResponse(generatedAccessToken, generatedRefreshToken);
    }

    public void logout(String token) {
        if (!jwtProvider.isValid(token) || !jwtProvider.isRefreshToken(token)) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }

        Long extractUserId = jwtProvider.extractUserId(token);
        refreshTokenRepository.delete(extractUserId);
    }
}