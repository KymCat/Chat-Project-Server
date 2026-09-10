package com.project.ChatProject.dto.response;

import com.project.ChatProject.entity.enums.ChatRoomType;

public record ChatRoomCreateResponse(
        Long roomId,
        ChatRoomType type,
        String name
) {
}
