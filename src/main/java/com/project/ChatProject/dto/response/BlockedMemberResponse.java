package com.project.ChatProject.dto.response;

import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.MemberBlock;
import com.project.ChatProject.entity.enums.MemberStatus;

public record BlockedMemberResponse(
        Long memberId,
        String displayName,
        MemberStatus status
) {

    public static BlockedMemberResponse from(MemberBlock memberBlock) {
        Member blockedMember = memberBlock.getBlocked();

        return new BlockedMemberResponse(
                blockedMember.getId(),
                resolveDisplayName(blockedMember),
                blockedMember.getStatus()
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
