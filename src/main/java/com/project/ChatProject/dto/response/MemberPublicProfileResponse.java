package com.project.ChatProject.dto.response;

import com.project.ChatProject.dto.enums.ProfileRelationStatus;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.enums.MemberStatus;

// 타인용 프로필 Record
public record MemberPublicProfileResponse(
        Long memberId,
        String displayName,
        String statusMessage,
        ProfileRelationStatus relationStatus,
        MemberStatus status
) {

    public static MemberPublicProfileResponse from(
            Member member,
            ProfileRelationStatus relationStatus
    ) {
        return switch (member.getStatus()) {
            case ACTIVE -> active(member, relationStatus);
            case SUSPENDED -> inactive(
                    member,
                    "비활성화된 회원",
                    relationStatus
            );
            case WITHDRAWN -> inactive(
                    member,
                    "탈퇴한 유저",
                    relationStatus
            );
        };
    }

    private static MemberPublicProfileResponse active(
            Member member,
            ProfileRelationStatus relationStatus
    ) {
        return new MemberPublicProfileResponse(
                member.getId(),
                member.getNickname(),
                member.getStatusMessage(),
                relationStatus,
                member.getStatus()
        );
    }

    private static MemberPublicProfileResponse inactive(
            Member member,
            String displayName,
            ProfileRelationStatus relationStatus
    ) {
        return new MemberPublicProfileResponse(
                member.getId(),
                displayName,
                null,
                relationStatus,
                member.getStatus()
        );
    }
}
