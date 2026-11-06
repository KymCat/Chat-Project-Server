package com.project.ChatProject.profile.google;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(
        prefix = "profile-image.google-download"
)
public record GoogleProfileImageDownloadProperties(

        @NotNull
        Duration connectTimeout,

        @NotNull
        Duration readTimeout
) {
}
