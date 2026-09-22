package com.project.ChatProject.dto.response;

import com.project.ChatProject.entity.ChatRoomMember;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.enums.ChatRoomMemberRole;
import com.project.ChatProject.entity.enums.MemberStatus;

import java.time.Instant;

public record ChatRoomMemberResponse(
        Long memberId,
        String displayName,
        ChatRoomMemberRole role,
        Instant joinedAt
) {
    public static ChatRoomMemberResponse from(ChatRoomMember chatRoomMember) {
        Member member = chatRoomMember.getMember();
        String displayName = resolveDisplayName(member);

        return new ChatRoomMemberResponse(
                member.getId(),
                displayName,
                chatRoomMember.getRole(),
                chatRoomMember.getJoinedAt()
        );
    }

    private static String resolveDisplayName(Member member) {
        if (member.getStatus() == MemberStatus.WITHDRAWN) {
            return "탈퇴한 유저";
        }

        if (member.getStatus() == MemberStatus.SUSPENDED) {
            return "비활성화된 회원";
        }

        return member.getNickname();
    }
}
