package com.project.ChatProject.dto.response;

import com.project.ChatProject.entity.Friendship;
import com.project.ChatProject.entity.Member;

public record FriendListResponse(
        Long memberId,
        String email,
        String nickname,
        String statusMessage
) {

    public static FriendListResponse from(
            Friendship friendship,
            Long myMemberId
    ) {
        Member target = friendship.getReceiver().getId().equals(myMemberId)
                ? friendship.getRequester()
                : friendship.getReceiver();

        return new FriendListResponse(
                target.getId(),
                target.getEmail(),
                target.getNickname(),
                target.getStatusMessage()
        );
    }
}
