package com.project.ChatProject.invite;

import java.time.Instant;

public record ChatRoomInviteLink(
        Long roomId,
        String token,
        Instant expiresAt
) {
    public static ChatRoomInviteLink of(
            Long roomId,
            String token,
            Instant expiresAt
    ) {
      return new ChatRoomInviteLink(roomId, token, expiresAt);
    }
}
