package com.project.ChatProject.dto.response;

import com.project.ChatProject.entity.Member;

public record MemberProfileResponse(
        String nickname,
        String email,
        String profileImageUrl,
        boolean hasStoredProfileImage
) {
    public static MemberProfileResponse from(Member member) {
        return new MemberProfileResponse(
                member.getNickname(),
                member.getEmail(),
                member.getProfileImageUrl(),
                member.hasStoredProfileImage()
        );
    }
}
