package com.project.ChatProject.reauth;

import com.project.ChatProject.dto.enums.ReauthenticationPurpose;
import com.project.ChatProject.entity.enums.SocialProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SocialReauthenticationAttemptStore {

    private static final String RE_AUTH_INTENT_TOKEN_KEY_PREFIX = "auth:reauth:intent:";
    private static final String MEMBER_ID_FIELD = "memberId";
    private static final String SESSION_ID_FIELD = "sessionId";
    private static final String PURPOSE_FIELD = "purpose";
    private static final String PROVIDER_FIELD = "provider";
    private static final Duration EXPIRATION = Duration.ofMinutes(5);

    private final ReauthenticationTokenGenerator generator;
    private final StringRedisTemplate redisTemplate;

    public String create(
            Long memberId,
            String sessionId,
            SocialProvider provider,
            ReauthenticationPurpose purpose
    ) {
        String token = generator.generate();
        String tokenKey = createReAuthIntentTokenKey(token);

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
                                purpose.name(),
                                PROVIDER_FIELD,
                                provider.name()
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
    private String createReAuthIntentTokenKey(String token) {
        return RE_AUTH_INTENT_TOKEN_KEY_PREFIX
                + "{"
                + token
                + "}";
    }
}
