package com.project.ChatProject.dto.response;

public record DirectChatResponse(

        Long roomId,
        ChatMessageResponse message
) {

    public static DirectChatResponse of(
            Long roomId,
            ChatMessageResponse message
    ) {
        return new DirectChatResponse(roomId, message);
    }
}
