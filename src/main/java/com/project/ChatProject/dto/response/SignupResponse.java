package com.project.ChatProject.dto.response;

import com.project.ChatProject.entity.Member;

public record SignupResponse(
        String email,
        String nickname
) {

    public static SignupResponse from(
            Member member
    ) {
        return new SignupResponse(
                member.getEmail(),
                member.getNickname()
        );
    }
}