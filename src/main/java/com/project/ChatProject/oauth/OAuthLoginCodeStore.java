package com.project.ChatProject.oauth;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OAuthLoginCodeStore {

    private static final String KEY_PREFIX = "auth:oauth-login-code:";
    private static final long EXPIRATION = 60;
    private final StringRedisTemplate redisTemplate;

    public String create(
            String sessionId,
            String accessToken
    ) {
        String code = UUID.randomUUID().toString();
        String key = createKey(sessionId, code);

        redisTemplate.opsForValue().set(
                key,
                accessToken,
                Duration.ofSeconds(EXPIRATION)
        );

        return code;
    }

    public Optional<String> consume(
            String code,
            String sessionId
    ) {
        String key = createKey(sessionId, code);

        String accessToken =
                redisTemplate.opsForValue().getAndDelete(key);

        return Optional.ofNullable(accessToken);
    }

    private String createKey(String sessionId, String code) {
        return KEY_PREFIX
                + "{" + sessionId + "}:"
                + code;
    }
}
