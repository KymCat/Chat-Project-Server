package com.project.ChatProject.dto.request;

import com.project.ChatProject.dto.enums.ReauthenticationPurpose;
import com.project.ChatProject.entity.enums.SocialProvider;
import jakarta.validation.constraints.NotNull;

public record SocialReauthenticationStartRequest(

        @NotNull(message = "소셜 프로바이더 입력은 필수입니다.")
        SocialProvider provider,

        @NotNull(message = "재인증 목적 입력은 필수입니다.")
        ReauthenticationPurpose purpose
) {
}
