package com.project.ChatProject.dto.event;

import com.project.ChatProject.entity.enums.ChatRoomEventType;

public record ChatRoomEvent(
        ChatRoomEventType eventType,
        Long roomId,
        String name
) {
    public static ChatRoomEvent updated(Long roomId, String name) {
        return new ChatRoomEvent(
                ChatRoomEventType.UPDATED,
                roomId,
                name
        );
    }

    public static ChatRoomEvent deleted(Long roomId, String name) {
        return new ChatRoomEvent(
                ChatRoomEventType.DELETED,
                roomId,
                name
        );
    }
}
