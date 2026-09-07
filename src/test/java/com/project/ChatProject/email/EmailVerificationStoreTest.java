package com.project.ChatProject.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationStoreTest {

    private static final String EMAIL_CODE_KEY =
            "auth:email-verification:{1}";
    private static final String COOLDOWN_KEY =
            "auth:email-verification:cooldown:{1}";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private EmailVerificationStore verificationStore;
    private EmailVerificationProperties properties;

    @BeforeEach
    void setUp() {
        properties = new EmailVerificationProperties(
                Duration.ofMinutes(5),
                Duration.ofSeconds(60),
                5,
                "no-reply@chatproject.local"
        );
        verificationStore = new EmailVerificationStore(
                properties,
                redisTemplate
        );
    }

    @Test
    void saveStoresHashAndInitialAttemptCountWithExpiration() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        verificationStore.save(1L, "code-hash");

        verify(hashOperations).putAll(
                EMAIL_CODE_KEY,
                Map.of(
                        "codeHash",
                        "code-hash",
                        "attemptCount",
                        "0"
                )
        );
        verify(redisTemplate).expire(
                EMAIL_CODE_KEY,
                properties.codeExpiration()
        );
    }

    @Test
    void findCodeHashReturnsSavedHash() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get(EMAIL_CODE_KEY, "codeHash"))
                .thenReturn("code-hash");

        Optional<String> result =
                verificationStore.findCodeHashByMemberId(1L);

        assertThat(result).contains("code-hash");
    }

    @Test
    void findCodeHashReturnsEmptyWhenVerificationDoesNotExist() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get(EMAIL_CODE_KEY, "codeHash"))
                .thenReturn(null);

        Optional<String> result =
                verificationStore.findCodeHashByMemberId(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void incrementAttemptCountUsesAtomicRedisIncrement() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.increment(
                EMAIL_CODE_KEY,
                "attemptCount",
                1
        )).thenReturn(3L);
        when(hashOperations.get(EMAIL_CODE_KEY, "codeHash"))
                .thenReturn("code-hash");

        Optional<Long> result =
                verificationStore.incrementAttemptCount(1L);

        assertThat(result).contains(3L);
    }

    @Test
    void incrementAttemptCountDeletesOrphanWhenCodeExpired() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.increment(
                EMAIL_CODE_KEY,
                "attemptCount",
                1
        )).thenReturn(1L);
        when(hashOperations.get(EMAIL_CODE_KEY, "codeHash"))
                .thenReturn(null);

        Optional<Long> result =
                verificationStore.incrementAttemptCount(1L);

        assertThat(result).isEmpty();
        verify(redisTemplate).delete(EMAIL_CODE_KEY);
    }

    @Test
    void tryStartResendCooldownStoresKeyOnlyWhenAbsent() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                COOLDOWN_KEY,
                "1",
                properties.resendCooldown()
        )).thenReturn(true);

        boolean result =
                verificationStore.tryStartResendCooldown(1L);

        assertThat(result).isTrue();
    }

    @Test
    void tryStartResendCooldownRejectsExistingKey() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                COOLDOWN_KEY,
                "1",
                properties.resendCooldown()
        )).thenReturn(false);

        boolean result =
                verificationStore.tryStartResendCooldown(1L);

        assertThat(result).isFalse();
    }
}
