package com.project.ChatProject.reauth;

import com.project.ChatProject.dto.enums.ReauthenticationPurpose;
import com.project.ChatProject.entity.enums.SocialProvider;

public record SocialReauthenticationAttempt(
        Long memberId,
        String sessionId,
        SocialProvider provider,
        ReauthenticationPurpose purpose
) {
}
