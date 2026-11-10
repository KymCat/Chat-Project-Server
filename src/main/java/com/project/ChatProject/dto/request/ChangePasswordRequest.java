package com.project.ChatProject.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(

        @NotBlank(message = "비밀번호를 입력해주세요.")
        @Size(
                min = 8,
                max = 30,
                message = "비밀번호는 8자 이상 30자 이하여야 합니다."
        )
        @Pattern(
                regexp = "^(?=.*[a-zA-Z])(?=.*\\d)[a-zA-Z\\d!@#$%^&*()_+=-]+$",
                message = "비밀번호는 영문, 숫자를 포함한 8~30자여야 합니다."
        )
        String newPassword
) {
}
