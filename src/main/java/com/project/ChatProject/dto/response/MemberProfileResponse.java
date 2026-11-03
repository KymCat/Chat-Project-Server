package com.project.ChatProject.dto.response;

import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.enums.MemberStatus;

public record MemberProfileResponse(
        String nickname,
        String email,
        String statusMessage,
        MemberStatus status,
        boolean usesStoredProfileImage
) {
    public static MemberProfileResponse from(Member member) {
        return new MemberProfileResponse(
                member.getNickname(),
                member.getEmail(),
                member.getStatusMessage(),
                member.getStatus(),
                member.hasStoredProfileImage()
        );
    }
}
