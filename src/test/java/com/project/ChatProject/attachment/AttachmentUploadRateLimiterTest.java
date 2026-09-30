package com.project.ChatProject.attachment;

import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentUploadRateLimiterTest {

    private static final Long MEMBER_ID = 1L;
    private static final String RATE_LIMIT_KEY =
            "rate-limit:attachment-upload:" + MEMBER_ID;
    private static final int MAX_REQUESTS = 10;
    private static final Duration WINDOW = Duration.ofSeconds(60);

    @Mock
    private StringRedisTemplate redisTemplate;

    private AttachmentUploadRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        AttachmentUploadRateLimitProperties properties =
                new AttachmentUploadRateLimitProperties(
                        MAX_REQUESTS,
                        WINDOW
                );

        rateLimiter = new AttachmentUploadRateLimiter(
                redisTemplate,
                properties
        );
    }

    @Test
    void allowsRequestAtConfiguredLimit() {
        stubRequestCount(10L);

        assertThatCode(() -> rateLimiter.checkAllowed(MEMBER_ID))
                .doesNotThrowAnyException();

        verify(redisTemplate).execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of(RATE_LIMIT_KEY)),
                eq(String.valueOf(WINDOW.toMillis()))
        );
    }

    @Test
    void rejectsRequestAboveConfiguredLimit() {
        stubRequestCount(11L);

        assertRateLimitExceeded();
    }

    @Test
    void rejectsRequestWhenRedisReturnsNull() {
        stubRequestCount(null);

        assertRateLimitExceeded();
    }

    private void stubRequestCount(Long requestCount) {
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of(RATE_LIMIT_KEY)),
                eq(String.valueOf(WINDOW.toMillis()))
        )).thenReturn(requestCount);
    }

    private void assertRateLimitExceeded() {
        assertThatThrownBy(() -> rateLimiter.checkAllowed(MEMBER_ID))
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        ErrorCode
                                                .ATTACHMENT_UPLOAD_TOO_MANY_REQUESTS
                                )
                );
    }
}
