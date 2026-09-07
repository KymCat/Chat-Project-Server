package com.project.ChatProject.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EmailVerificationConfirmRequest(

        @NotBlank(message = "이메일 인증 코드는 필수입니다.")
        @Pattern(
                regexp = "^\\d{6}$",
                message = "이메일 인증 코드는 6자리 숫자여야 합니다."
        )
        String code
) {
}
