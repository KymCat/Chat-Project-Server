package com.project.ChatProject.dto.response;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String sessionId
) {
}
