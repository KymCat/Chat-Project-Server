package com.project.ChatProject.jwt.refresh;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "refresh-token")
public record RefreshTokenProperties(
        Duration expiration
) {
}
