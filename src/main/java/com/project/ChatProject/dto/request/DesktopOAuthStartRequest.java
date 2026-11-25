package com.project.ChatProject.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DesktopOAuthStartRequest(

        @NotBlank
        @Pattern(
                regexp = "[A-Za-z0-9_-]{43}",
                message = "codeChallenge는 43자의 Base64URL 문자열이어야 합니다."
        )
        String codeChallenge
) {
}
