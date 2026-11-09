package com.project.ChatProject.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateNicknameRequest(

        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(
                min = 2,
                max = 30,
                message = "닉네임은 2자 이상 30자 이하여야 합니다."
        )
        @Pattern(
                regexp = "^[a-zA-Z0-9가-힣]+$",
                message = "닉네임은 한글, 영어, 숫자만 가능합니다."
        )
        String newNickname
) {
}
