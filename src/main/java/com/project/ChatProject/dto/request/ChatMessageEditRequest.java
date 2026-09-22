package com.project.ChatProject.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatMessageEditRequest(
        @NotBlank(message = "메시지 내용을 입력해주세요.")
        @Size(
                max = 1000,
                message = "메시지는 1000자 이하로 입력해주세요."
        )
        String content
) {
}
