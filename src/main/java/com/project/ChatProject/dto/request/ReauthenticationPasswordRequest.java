package com.project.ChatProject.dto.request;

import com.project.ChatProject.dto.enums.ReauthenticationPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReauthenticationPasswordRequest(

        @NotBlank(message = "비밀번호를 입력해주세요.")
        @Size(
                min = 8,
                max = 30,
                message = "비밀번호는 8자 이상 30자 이하여야 합니다."
        )
        String password,

        @NotNull(message = "재인증 목적 입력은 필수입니다.")
        ReauthenticationPurpose purpose

) {
}
