package com.project.ChatProject.dto.response;

import com.project.ChatProject.entity.Member;

public record SignupResponse(
        String profileImageUrl,
        String email,
        String nickname
) {
    public static SignupResponse from(Member saved) {
        return new SignupResponse(
                saved.getProfileImageUrl(),
                saved.getEmail(),
                saved.getNickname());
    }
}
