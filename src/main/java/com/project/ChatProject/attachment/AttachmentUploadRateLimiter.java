package com.project.ChatProject.attachment;

import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AttachmentUploadRateLimiter {
    private static final String KEY_PREFIX =
            "rate-limit:attachment-upload:";

    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local count = redis.call('INCR', KEYS[1])

                    if count == 1 then
                        redis.call('PEXPIRE', KEYS[1], ARGV[1])
                    end

                    return count
                    """,
                    Long.class
            );

    private final StringRedisTemplate redisTemplate;
    private final AttachmentUploadRateLimitProperties properties;

    public void checkAllowed(Long memberId) {
        String key = KEY_PREFIX + memberId;

        Long requestCount = redisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                List.of(key),
                String.valueOf(properties.window().toMillis())
        );

        if (requestCount == null ||
                requestCount > properties.maxRequests()) {
            throw new CustomException(
                    ErrorCode.ATTACHMENT_UPLOAD_TOO_MANY_REQUESTS
            );
        }
    }
}
