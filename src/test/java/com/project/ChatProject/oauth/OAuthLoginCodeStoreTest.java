package com.project.ChatProject.oauth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthLoginCodeStoreTest {

    private static final String SESSION_ID = "session-id";
    private static final String ACCESS_TOKEN = "access-token";
    private static final Duration LOGIN_CODE_TTL = Duration.ofSeconds(60);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private OAuthLoginCodeStore store;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void createStoresAccessTokenWithSessionBoundKeyAndExpiration() {
        String code = store.create(SESSION_ID, ACCESS_TOKEN);

        assertThat(code).isNotBlank();
        verify(valueOperations).set(
                "auth:oauth-login-code:{session-id}:" + code,
                ACCESS_TOKEN,
                LOGIN_CODE_TTL
        );
    }

    @Test
    void consumeReturnsAccessTokenAndDeletesCodeAtomically() {
        when(valueOperations.getAndDelete(
                "auth:oauth-login-code:{session-id}:login-code"
        )).thenReturn(ACCESS_TOKEN);

        Optional<String> result = store.consume(
                "login-code",
                SESSION_ID
        );

        assertThat(result).contains(ACCESS_TOKEN);
        verify(valueOperations).getAndDelete(
                "auth:oauth-login-code:{session-id}:login-code"
        );
    }

    @Test
    void consumeReturnsEmptyWhenCodeDoesNotExist() {
        when(valueOperations.getAndDelete(
                "auth:oauth-login-code:{session-id}:missing-code"
        )).thenReturn(null);

        Optional<String> result = store.consume(
                "missing-code",
                SESSION_ID
        );

        assertThat(result).isEmpty();
    }
}
