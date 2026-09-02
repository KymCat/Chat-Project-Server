package com.project.ChatProject.jwt.refresh;

public record RefreshTokenSession(
        Long memberId,
        String refreshTokenHash
) {
}
