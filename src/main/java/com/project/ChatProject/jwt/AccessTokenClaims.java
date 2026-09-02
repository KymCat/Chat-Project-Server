package com.project.ChatProject.jwt;

import java.time.Instant;

public record AccessTokenClaims(
        Long memberId,
        String tokenId,
        String sessionId,
        boolean emailVerified,
        Instant issuedAt,
        Instant expiresAt
) {
}
