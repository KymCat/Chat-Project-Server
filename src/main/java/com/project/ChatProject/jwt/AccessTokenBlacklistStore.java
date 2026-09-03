package com.project.ChatProject.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class AccessTokenBlacklistStore {
    private final StringRedisTemplate redisTemplate;

    private static final String BLACK_LIST_PREFIX = "auth:blacklist:";
    private static final String BLACKLIST_VALUE = "BLACK";

    public void save(String jti, Instant expiresAt) {
        String blacklistKey = createBlacklistKey(jti);

        Duration ttl = Duration.between(Instant.now(), expiresAt);
        redisTemplate.opsForValue().set(
                blacklistKey,
                BLACKLIST_VALUE,
                ttl
        );
    }

    public boolean exists(String jti) {
        return Boolean.TRUE.equals(
                redisTemplate.hasKey(createBlacklistKey(jti))
        );
    }

    private String createBlacklistKey(String jti) {
        return BLACK_LIST_PREFIX
                + "{"
                + jti
                + "}";
    }
}
