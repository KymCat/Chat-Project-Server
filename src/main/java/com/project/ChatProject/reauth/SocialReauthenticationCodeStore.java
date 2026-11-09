package com.project.ChatProject.reauth;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SocialReauthenticationCodeStore {

    private static final String KEY_PREFIX =
            "auth:reauth:exchange-code:";
    private static final Duration EXPIRATION =
            Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;
    private final ReauthenticationTokenGenerator generator;

    public String create(
            String sessionId,
            String reauthenticationToken
    ) {
        String code = generator.generate();
        String key = createKey(sessionId, code);

        redisTemplate.opsForValue().set(
                key,
                reauthenticationToken,
                EXPIRATION
        );

        return code;
    }

    public Optional<String> consume(
            String code,
            String sessionId
    ) {
        String key = createKey(sessionId, code);
        String reauthenticationToken = redisTemplate
                .opsForValue()
                .getAndDelete(key);

        return Optional.ofNullable(reauthenticationToken);
    }

    private String createKey(
            String sessionId,
            String code
    ) {
        return KEY_PREFIX
                + "{" + sessionId + "}:"
                + code;
    }
}
