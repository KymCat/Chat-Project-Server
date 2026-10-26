package com.project.ChatProject.config.profile;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "profile-image")
public record ProfileImageProperties(
        @NotNull DataSize maxSize,
        @Min(1) int maxWidth,
        @Min(1) int maxHeight,
        @Min(1) long maxPixels
) {
}
