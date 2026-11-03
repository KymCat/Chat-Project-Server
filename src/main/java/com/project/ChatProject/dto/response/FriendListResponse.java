package com.project.ChatProject.dto.response;

import com.project.ChatProject.entity.Friendship;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.enums.MemberStatus;

public record FriendListResponse(
        Long memberId,
        String displayName,
        String statusMessage,
        MemberStatus status
) {

    public static FriendListResponse from(
            Friendship friendship,
            Long myMemberId
    ) {
        Member target = friendship.getReceiver().getId().equals(myMemberId)
                ? friendship.getRequester()
                : friendship.getReceiver();

        String displayName = resolveDisplayName(target);

        return new FriendListResponse(
                target.getId(),
                displayName,
                target.getStatus() == MemberStatus.ACTIVE
                        ? target.getStatusMessage()
                        : null,
                target.getStatus()
        );
    }

    private static String resolveDisplayName(Member target) {
        if (target.getStatus() == MemberStatus.SUSPENDED) {
            return "비활성화된 회원";
        }

        if (target.getStatus() == MemberStatus.WITHDRAWN) {
            return "탈퇴한 유저";
        }

        return target.getNickname();
    }
}
