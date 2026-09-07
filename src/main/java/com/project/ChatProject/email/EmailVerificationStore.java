package com.project.ChatProject.email;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class EmailVerificationStore {

    private static final String EMAIL_CODE_KEY_PREFIX =
            "auth:email-verification:";

    private static final String RESEND_COOLDOWN_KEY_PREFIX =
            "auth:email-verification:cooldown:";

    private static final String EMAIL_CODE_HASH_FIELD =
            "codeHash";

    private static final String EMAIL_ATTEMPT_COUNT_FIELD =
            "attemptCount";

    private static final String INITIAL_ATTEMPT_COUNT = "0";
    private static final String COOLDOWN_VALUE = "1";

    private final EmailVerificationProperties properties;
    private final StringRedisTemplate redisTemplate;

    public void save(
            Long memberId,
            String codeHash)
    {
        String emailCodeKey = createEmailCodeKey(memberId);

        redisTemplate.opsForHash().putAll(
                emailCodeKey,
                Map.of(
                        EMAIL_CODE_HASH_FIELD,
                        codeHash,
                        EMAIL_ATTEMPT_COUNT_FIELD,
                        INITIAL_ATTEMPT_COUNT
                )
        );

        redisTemplate.expire(
                emailCodeKey,
                properties.codeExpiration()
        );

    }

    public Optional<String> findCodeHashByMemberId(Long memberId) {
        Object codeHash = redisTemplate.opsForHash().get(
                createEmailCodeKey(memberId),
                EMAIL_CODE_HASH_FIELD
        );

        if (codeHash == null) {
            return Optional.empty();
        }

        return Optional.of(codeHash.toString());
    }

    public Optional<Long> incrementAttemptCount(Long memberId) {
        String emailCodeKey = createEmailCodeKey(memberId);

        Long attemptCount = redisTemplate.opsForHash().increment(
                emailCodeKey,
                EMAIL_ATTEMPT_COUNT_FIELD,
                1
        );

        Object codeHash = redisTemplate.opsForHash().get(
                emailCodeKey,
                EMAIL_CODE_HASH_FIELD
        );

        if (codeHash == null) {
            redisTemplate.delete(emailCodeKey);
            return Optional.empty();
        }

        return Optional.ofNullable(attemptCount);
    }

    public void deleteByMemberId(Long memberId) {
        redisTemplate.delete(
                createEmailCodeKey(memberId)
        );
    }

    public boolean tryStartResendCooldown(Long memberId) {
        Boolean created = redisTemplate.opsForValue().setIfAbsent(
                createResendCooldownKey(memberId),
                COOLDOWN_VALUE,
                properties.resendCooldown()
        );

        return Boolean.TRUE.equals(created);
    }

    public void deleteResendCooldown(Long memberId) {
        redisTemplate.delete(
                createResendCooldownKey(memberId)
        );
    }

    private String createEmailCodeKey(Long memberId) {
        return EMAIL_CODE_KEY_PREFIX
                + "{"
                + memberId
                + "}";
    }

    private String createResendCooldownKey(Long memberId) {
        return RESEND_COOLDOWN_KEY_PREFIX
                + "{"
                + memberId
                + "}";
    }
}
