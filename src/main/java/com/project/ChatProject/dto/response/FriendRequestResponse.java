package com.project.ChatProject.dto.response;

import com.project.ChatProject.entity.Friendship;

import java.time.Instant;

public record FriendRequestResponse(
        Long requestId,         // friendship table id(pK)
        Long targetMemberId,    // not me
        String targetNickname,
        Instant createdAt
) {

    public static FriendRequestResponse sentOf(
            Friendship friendship
    ) {
        return new FriendRequestResponse(
                friendship.getId(),
                friendship.getReceiver().getId(),
                friendship.getReceiver().getNickname(),
                friendship.getCreatedAt()
        );
    }

    public static FriendRequestResponse receivedOf(
            Friendship friendship
    ) {
        return new FriendRequestResponse(
                friendship.getId(),
                friendship.getRequester().getId(),
                friendship.getRequester().getNickname(),
                friendship.getCreatedAt()
        );
    }
}
