package com.project.ChatProject.config.storage;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;

@Validated
@ConfigurationProperties(prefix = "chat.storage")
public record StorageProperties(
        @NotNull Path localRoot,
        @NotNull DataSize maxImageSize,
        @NotNull DataSize maxFileSize
) {
}
