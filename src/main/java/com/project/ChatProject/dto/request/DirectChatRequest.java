package com.project.ChatProject.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DirectChatRequest(

        @NotNull(message = "채팅을 할 친구 선택은 필수입니다.")
        Long targetMemberId,

        @NotBlank(message = "보낼 메세지를 입력해주세요.")
        String content
) {
}
