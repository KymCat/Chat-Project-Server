package com.project.ChatProject.dto.response;

import com.project.ChatProject.invite.ChatRoomInviteLink;

import java.time.Instant;

public record ChatRoomInviteLinkResponse(
        Long roomId,
        String token,
        Instant expiresAt
) {

    public static ChatRoomInviteLinkResponse from(
            ChatRoomInviteLink inviteLink
    ) {
        return new ChatRoomInviteLinkResponse(
                inviteLink.roomId(),
                inviteLink.token(),
                inviteLink.expiresAt()
        );
    }
}
