package com.project.ChatProject.dto.request;

import jakarta.validation.constraints.*;

public record LoginRequest(

        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(
                max = 254,
                message = "이메일은 254자 이하여야 합니다."
        )
        String email,

        @NotBlank(message = "비밀번호를 입력해주세요.")
        String password
) {
}
