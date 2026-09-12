package com.project.ChatProject.dto.response;

import com.project.ChatProject.entity.ChatMessage;
import com.project.ChatProject.entity.Member;

public record ChatMessageResponse(
        String content,
        Long senderId,
        String senderNickname,
        String type,    // 입장 및 일반 메세지 구분
        Long roomId
) {
    public static ChatMessageResponse of(
            ChatMessage chatMessage,
            Member sender,
            String type
    )
    {
        return new ChatMessageResponse(
                chatMessage.getContent(),
                sender.getId(),
                sender.getNickname(),
                type,
                chatMessage.getChatRoom().getId()
        );
    }
}
