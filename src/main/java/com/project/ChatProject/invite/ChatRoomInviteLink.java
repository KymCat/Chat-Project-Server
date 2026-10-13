package com.project.ChatProject.invite;

import java.time.Instant;

public record ChatRoomInviteLink(
        Long roomId,
        String token,
        Instant expiresAt
) {
}
