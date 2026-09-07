package com.project.ChatProject.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "email-verification")
public record EmailVerificationProperties(
        Duration codeExpiration,
        Duration resendCooldown,
        int maxAttempts
) {
}
