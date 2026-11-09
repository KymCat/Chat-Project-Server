package com.project.ChatProject.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SocialReauthenticationCodeExchangeRequest(
        @NotBlank(message = "소셜 재인증 코드는 필수입니다.")
        String code
) {
}
