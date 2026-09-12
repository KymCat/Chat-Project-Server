package com.project.ChatProject.dto.response;

public record ChatRoomJoinResponse(
        GroupChatRoomResponse chatRoom,
        ChatMessageResponse chatMessage
) {
}
