package com.project.ChatProject.oauth;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/*
    Electron 에서 소셜 로그인을 시도하는지 검증하기위한 클래스
 */
@Component
@RequiredArgsConstructor
public class DesktopOAuthAttemptStore {

    private static final String KEY_PREFIX = "auth:desktop-oauth-attempt:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;

    public String create(String codeChallenge) {
        String attemptId = UUID.randomUUID().toString();

        redisTemplate.opsForValue().set(
                KEY_PREFIX + attemptId,
                codeChallenge,
                TTL
        );

        return attemptId;
    }

    public Optional<String> consume(String attemptId) {
        String codeChallenge = redisTemplate.opsForValue()
                .getAndDelete(KEY_PREFIX + attemptId);

        return Optional.ofNullable(codeChallenge);
    }
}
