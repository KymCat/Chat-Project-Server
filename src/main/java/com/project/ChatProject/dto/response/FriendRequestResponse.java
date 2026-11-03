package com.project.ChatProject.dto.response;

import com.project.ChatProject.entity.Friendship;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.enums.MemberStatus;

import java.time.Instant;

public record FriendRequestResponse(
        Long requestId,         // friendship table id(pK)
        Long targetMemberId,    // not me
        String targetDisplayName,
        Instant createdAt
) {

    public static FriendRequestResponse sentOf(
            Friendship friendship
    ) {
        Member target = friendship.getReceiver();

        return new FriendRequestResponse(
                friendship.getId(),
                target.getId(),
                resolveDisplayName(target),
                friendship.getCreatedAt()
        );
    }

    public static FriendRequestResponse receivedOf(
            Friendship friendship
    ) {
        Member target = friendship.getRequester();

        return new FriendRequestResponse(
                friendship.getId(),
                target.getId(),
                resolveDisplayName(target),
                friendship.getCreatedAt()
        );
    }

    private static String resolveDisplayName(Member member) {
        if (member.getStatus() == MemberStatus.SUSPENDED) {
            return "비활성화된 회원";
        }

        if (member.getStatus() == MemberStatus.WITHDRAWN) {
            return "탈퇴한 유저";
        }

        return member.getNickname();
    }
}
