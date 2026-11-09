package com.project.ChatProject.reauth;

import com.project.ChatProject.dto.enums.ReauthenticationPurpose;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ReauthenticationTokenStore {

    private static final String RE_AUTH_TOKEN_KEY_PREFIX = "auth:reauth:";
    private static final String MEMBER_ID_FIELD = "memberId";
    private static final String SESSION_ID_FIELD = "sessionId";
    private static final String PURPOSE_FIELD = "purpose";
    private static final Duration EXPIRATION = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;
    private final ReauthenticationTokenGenerator generator;

    public String create(
            Long memberId,
            String sessionId,
            ReauthenticationPurpose purpose
    ) {
        String token = generator.generate();
        String tokenKey = createReAuthTokenKey(token);

        redisTemplate
                .opsForHash()
                .putAll(
                        tokenKey,
                        Map.of(
                                MEMBER_ID_FIELD,
                                memberId.toString(),
                                SESSION_ID_FIELD,
                                sessionId,
                                PURPOSE_FIELD,
                                purpose.name()
                        )
                );

        redisTemplate
                .expire(
                        tokenKey,
                        EXPIRATION
                );

        return token;
    }

    // === private Method ===
    private String createReAuthTokenKey(String token) {
        return RE_AUTH_TOKEN_KEY_PREFIX
                + "{"
                + token
                + "}";
    }
}
