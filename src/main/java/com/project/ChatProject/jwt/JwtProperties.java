package com.project.ChatProject.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        Duration accessTokenExpiration,
        String secret
) {
}
