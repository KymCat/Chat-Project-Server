package com.project.ChatProject.dto.response;

import com.project.ChatProject.entity.ChatRoom;

import java.time.Instant;

public record GroupChatRoomResponse(
        Long roomId,
        String name,
        Instant lastMessageAt
) {
    public static GroupChatRoomResponse of(ChatRoom chatRoom) {
        return new GroupChatRoomResponse(
                chatRoom.getId(),
                chatRoom.getName(),
                chatRoom.getLastMessageAt()
        );
    }
}
