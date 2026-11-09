package com.project.ChatProject.reauth;

import com.project.ChatProject.dto.enums.ReauthenticationPurpose;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReauthenticationTokenStoreTest {

    private static final String TOKEN = "reauthentication-token";
    private static final String TOKEN_KEY =
            "auth:reauth:{" + TOKEN + "}";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private ReauthenticationTokenGenerator generator;

    private ReauthenticationTokenStore tokenStore;

    @BeforeEach
    void setUp() {
        tokenStore = new ReauthenticationTokenStore(
                redisTemplate,
                generator
        );
    }

    @Test
    void createStoresGrantWithFiveMinuteExpiration() {
        when(generator.generate()).thenReturn(TOKEN);
        when(redisTemplate.opsForHash())
                .thenReturn(hashOperations);

        String result = tokenStore.create(
                1L,
                "session-id",
                ReauthenticationPurpose.LOGOUT_ALL
        );

        assertThat(result).isEqualTo(TOKEN);
        verify(hashOperations).putAll(
                TOKEN_KEY,
                Map.of(
                        "memberId",
                        "1",
                        "sessionId",
                        "session-id",
                        "purpose",
                        "LOGOUT_ALL"
                )
        );
        verify(redisTemplate).expire(
                TOKEN_KEY,
                Duration.ofMinutes(5)
        );
    }

    @Test
    void consumeSucceedsWhenLuaScriptConsumesToken() {
        stubConsumeResult(1L);

        assertThatCode(() -> tokenStore.consume(
                TOKEN,
                1L,
                "session-id",
                ReauthenticationPurpose.LOGOUT_ALL
        )).doesNotThrowAnyException();

        verify(redisTemplate).execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of(TOKEN_KEY)),
                eq("1"),
                eq("session-id"),
                eq("LOGOUT_ALL")
        );
    }

    @Test
    void consumeRejectsMissingMismatchedOrAlreadyConsumedToken() {
        stubConsumeResult(0L);

        assertInvalidToken(() -> tokenStore.consume(
                TOKEN,
                1L,
                "session-id",
                ReauthenticationPurpose.LOGOUT_ALL
        ));
    }

    @Test
    void consumeRejectsNullRedisResult() {
        stubConsumeResult(null);

        assertInvalidToken(() -> tokenStore.consume(
                TOKEN,
                1L,
                "session-id",
                ReauthenticationPurpose.LOGOUT_ALL
        ));
    }

    @Test
    void consumeRejectsInvalidArgumentsWithoutRedisCall() {
        assertInvalidToken(() -> tokenStore.consume(
                " ",
                1L,
                "session-id",
                ReauthenticationPurpose.LOGOUT_ALL
        ));
        assertInvalidToken(() -> tokenStore.consume(
                TOKEN,
                null,
                "session-id",
                ReauthenticationPurpose.LOGOUT_ALL
        ));
        assertInvalidToken(() -> tokenStore.consume(
                TOKEN,
                1L,
                null,
                ReauthenticationPurpose.LOGOUT_ALL
        ));
        assertInvalidToken(() -> tokenStore.consume(
                TOKEN,
                1L,
                "session-id",
                null
        ));

        verifyNoInteractions(redisTemplate);
    }

    private void stubConsumeResult(Long result) {
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of(TOKEN_KEY)),
                eq("1"),
                eq("session-id"),
                eq("LOGOUT_ALL")
        )).thenReturn(result);
    }

    private void assertInvalidToken(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable
    ) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        ErrorCode.INVALID_REAUTHENTICATION_TOKEN
                                )
                );
    }
}
