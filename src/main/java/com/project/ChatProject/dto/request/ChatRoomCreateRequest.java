package com.project.ChatProject.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRoomCreateRequest(
        @NotBlank(message = "채팅방 이름은 필수입니다.")
        @Size(
                max = 30,
                message = "채팅방 이름은 1자 이상 30자 이하여야 합니다."
        )
        String name
) {
    public ChatRoomCreateRequest {
        if (name != null) {
            name = name.strip();
        }
    }
}
