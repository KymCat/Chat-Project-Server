package com.project.ChatProject.dto.response;

public record DirectChatRoomResponse(
        Long roomId
) {

    public static DirectChatRoomResponse found(Long roomId) {
        return new DirectChatRoomResponse(roomId);
    }

    public static DirectChatRoomResponse notFound() {
        return new DirectChatRoomResponse(null);
    }
}
