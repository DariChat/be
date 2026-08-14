package com.talkie.chat.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class EmailVerificationRepository {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String CODE_PREFIX = "email-verification:";
    private static final String COOLDOWN_PREFIX = "email-verification-cooldown:";

    public void saveCode(String email, String code, long expirationMs) {
        redisTemplate.opsForValue().set(CODE_PREFIX + email, code, expirationMs, TimeUnit.MILLISECONDS);
    }

    public String findCode(String email) {
        return redisTemplate.opsForValue().get(CODE_PREFIX + email);
    }

    public void deleteCode(String email) {
        redisTemplate.delete(CODE_PREFIX + email);
    }

    public boolean tryMarkCooldown(String email, long cooldownMs) {
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(COOLDOWN_PREFIX + email, "1", cooldownMs, TimeUnit.MILLISECONDS);
        return Boolean.TRUE.equals(success);
    }
}