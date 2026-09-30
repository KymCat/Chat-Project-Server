package com.project.ChatProject.attachment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(
        prefix = "chat.attachment-upload-rate-limit"
)
public record AttachmentUploadRateLimitProperties(

        @Positive
        int maxRequests,

        @NotNull
        Duration window
) {
}
