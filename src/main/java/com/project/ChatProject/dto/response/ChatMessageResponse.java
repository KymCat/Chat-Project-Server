package com.project.ChatProject.dto.response;

public record ChatMessageResponse(
        String content,
        Long senderId,
        String senderNickname,
        String type,
        String roomId
) {
}
