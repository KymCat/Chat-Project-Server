package com.project.ChatProject.oauth;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

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
}
