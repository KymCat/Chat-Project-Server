package com.project.ChatProject.dto.response;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String sessionId
) {
}
