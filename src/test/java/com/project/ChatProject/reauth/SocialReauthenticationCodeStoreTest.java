package com.project.ChatProject.reauth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class SocialReauthenticationCodeStoreTest {

    private static final String CODE = "reauthentication-code";
    private static final String KEY =
            "auth:reauth:exchange-code:{session-id}:" + CODE;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ReauthenticationTokenGenerator generator;

    private SocialReauthenticationCodeStore codeStore;

    @BeforeEach
    void setUp() {
        codeStore = new SocialReauthenticationCodeStore(
                redisTemplate,
                generator
        );
    }

    @Test
    void createStoresTokenForSixtySeconds() {
        when(generator.generate()).thenReturn(CODE);
        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        String result = codeStore.create(
                "session-id",
                "reauthentication-token"
        );

        assertThat(result).isEqualTo(CODE);
        verify(valueOperations).set(
                KEY,
                "reauthentication-token",
                Duration.ofSeconds(60)
        );
    }

    @Test
    void consumeAtomicallyReturnsAndDeletesToken() {
        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);
        when(valueOperations.getAndDelete(KEY))
                .thenReturn("reauthentication-token");

        Optional<String> result = codeStore.consume(
                CODE,
                "session-id"
        );

        assertThat(result).contains("reauthentication-token");
        verify(valueOperations).getAndDelete(KEY);
    }
}
