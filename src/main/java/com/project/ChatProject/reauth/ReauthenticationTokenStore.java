package com.project.ChatProject.reauth;

import com.project.ChatProject.dto.enums.ReauthenticationPurpose;
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
public class ReauthenticationTokenStore {

    private static final String RE_AUTH_TOKEN_KEY_PREFIX = "auth:reauth:";
    private static final String MEMBER_ID_FIELD = "memberId";
    private static final String SESSION_ID_FIELD = "sessionId";
    private static final String PURPOSE_FIELD = "purpose";
    private static final Duration EXPIRATION = Duration.ofMinutes(5);
    private static final RedisScript<Long> CONSUME_SCRIPT =
            loadScript(
                    "redis/reauthentication-token-consume.lua"
            );

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

    public void consume(
            String token,
            Long expectedMemberId,
            String expectedSessionId,
            ReauthenticationPurpose expectedPurpose
    ) {
        validateConsumeArguments(
                token,
                expectedMemberId,
                expectedSessionId,
                expectedPurpose
        );

        String tokenKey = createReAuthTokenKey(token);
        Long result = redisTemplate.execute(
                CONSUME_SCRIPT,
                List.of(tokenKey),
                expectedMemberId.toString(),
                expectedSessionId,
                expectedPurpose.name()
        );

        if (!Long.valueOf(1L).equals(result)) {
            throwInvalidReauthenticationToken();
        }
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

    private void validateConsumeArguments(
            String token,
            Long expectedMemberId,
            String expectedSessionId,
            ReauthenticationPurpose expectedPurpose
    ) {
        if (!StringUtils.hasText(token)
                || expectedMemberId == null
                || !StringUtils.hasText(expectedSessionId)
                || expectedPurpose == null) {
            throwInvalidReauthenticationToken();
        }
    }

    private void throwInvalidReauthenticationToken() {
        throw new CustomException(
                ErrorCode.INVALID_REAUTHENTICATION_TOKEN
        );
    }

    private String createReAuthTokenKey(String token) {
        return RE_AUTH_TOKEN_KEY_PREFIX
                + "{"
                + token
                + "}";
    }
}
