package com.project.ChatProject.jwt.refresh;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenStoreTest {

    private static final String REFRESH_TOKEN_KEY =
            "auth:refresh:{session-id}";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private RefreshTokenStore refreshTokenStore;

    @BeforeEach
    void setUp() {
        RefreshTokenProperties properties =
                new RefreshTokenProperties(
                        Duration.ofDays(14)
                );
        refreshTokenStore = new RefreshTokenStore(
                redisTemplate,
                properties
        );
    }

    @Test
    void activeSessionReturnsTrueForItsMember() {
        when(redisTemplate.opsForHash())
                .thenReturn(hashOperations);
        when(hashOperations.get(
                REFRESH_TOKEN_KEY,
                "memberId"
        )).thenReturn("1");

        boolean result = refreshTokenStore.isActiveSession(
                1L,
                "session-id"
        );

        assertThat(result).isTrue();
        verify(hashOperations).get(
                REFRESH_TOKEN_KEY,
                "memberId"
        );
    }

    @Test
    void sessionOwnedByAnotherMemberReturnsFalse() {
        when(redisTemplate.opsForHash())
                .thenReturn(hashOperations);
        when(hashOperations.get(
                REFRESH_TOKEN_KEY,
                "memberId"
        )).thenReturn("2");

        boolean result = refreshTokenStore.isActiveSession(
                1L,
                "session-id"
        );

        assertThat(result).isFalse();
    }

    @Test
    void missingSessionReturnsFalse() {
        when(redisTemplate.opsForHash())
                .thenReturn(hashOperations);
        when(hashOperations.get(
                REFRESH_TOKEN_KEY,
                "memberId"
        )).thenReturn(null);

        boolean result = refreshTokenStore.isActiveSession(
                1L,
                "session-id"
        );

        assertThat(result).isFalse();
    }

    @Test
    void invalidInputReturnsFalseWithoutRedisLookup() {
        assertThat(refreshTokenStore.isActiveSession(
                null,
                "session-id"
        )).isFalse();
        assertThat(refreshTokenStore.isActiveSession(
                1L,
                null
        )).isFalse();
        assertThat(refreshTokenStore.isActiveSession(
                1L,
                " "
        )).isFalse();

        verifyNoInteractions(redisTemplate);
    }
}
