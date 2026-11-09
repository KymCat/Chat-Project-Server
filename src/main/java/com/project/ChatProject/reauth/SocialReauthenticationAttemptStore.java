package com.project.ChatProject.reauth;

import com.project.ChatProject.dto.enums.ReauthenticationPurpose;
import com.project.ChatProject.entity.enums.SocialProvider;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
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
    private static final List<Object> ATTEMPT_FIELDS = List.of(
            MEMBER_ID_FIELD,
            SESSION_ID_FIELD,
            PROVIDER_FIELD,
            PURPOSE_FIELD
    );
    private static final RedisScript<Long> CONSUME_SCRIPT =
            loadScript(
                    "redis/social-reauthentication-attempt-consume.lua"
            );

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

    public SocialReauthenticationAttempt consume(
            String intentId
    ) {
        if (!StringUtils.hasText(intentId)) {
            throw invalidAttempt();
        }

        String tokenKey = createReAuthIntentTokenKey(intentId);
        List<Object> values = redisTemplate
                .opsForHash()
                .multiGet(
                        tokenKey,
                        ATTEMPT_FIELDS
                );

        if (values == null
                || values.size() != ATTEMPT_FIELDS.size()
                || values.stream().anyMatch(value -> value == null)) {
            throw invalidAttempt();
        }

        String memberIdValue = values.get(0).toString();
        String sessionId = values.get(1).toString();
        String providerValue = values.get(2).toString();
        String purposeValue = values.get(3).toString();

        SocialReauthenticationAttempt attempt = parseAttempt(
                memberIdValue,
                sessionId,
                providerValue,
                purposeValue
        );

        Long result = redisTemplate.execute(
                CONSUME_SCRIPT,
                List.of(tokenKey),
                memberIdValue,
                sessionId,
                providerValue,
                purposeValue
        );

        if (!Long.valueOf(1L).equals(result)) {
            throw invalidAttempt();
        }

        return attempt;
    }

    // === private Method ===
    private static RedisScript<Long> loadScript(String path) {
        DefaultRedisScript<Long> script =
                new DefaultRedisScript<>();

        script.setLocation(
                new ClassPathResource(path)
        );
        script.setResultType(Long.class);

        return script;
    }

    private SocialReauthenticationAttempt parseAttempt(
            String memberIdValue,
            String sessionId,
            String providerValue,
            String purposeValue
    ) {
        try {
            return new SocialReauthenticationAttempt(
                    Long.valueOf(memberIdValue),
                    sessionId,
                    SocialProvider.valueOf(providerValue),
                    ReauthenticationPurpose.valueOf(purposeValue)
            );
        } catch (IllegalArgumentException exception) {
            throw invalidAttempt();
        }
    }

    private CustomException invalidAttempt() {
        return new CustomException(
                ErrorCode.INVALID_SOCIAL_REAUTHENTICATION_ATTEMPT
        );
    }

    private String createReAuthIntentTokenKey(String token) {
        return RE_AUTH_INTENT_TOKEN_KEY_PREFIX
                + "{"
                + token
                + "}";
    }
}
