package com.project.ChatProject.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DesktopOAuthExchangeRequest(

        @NotBlank(message = "일회용 코드 입력은 필수입니다.")
        String code,

        @NotBlank(message = "codeVerifier는 필수입니다.")
        @Pattern(
                regexp = "[A-Za-z0-9._~-]{43,128}",
                message = "codeVerifier 형식이 올바르지 않습니다."
        )
        String codeVerifier
) {
}
