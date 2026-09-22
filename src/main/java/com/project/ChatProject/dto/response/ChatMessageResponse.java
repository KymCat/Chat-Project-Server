package com.project.ChatProject.dto.response;

import com.project.ChatProject.entity.ChatMessage;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.enums.ChatMessageType;

import java.time.Instant;

public record ChatMessageResponse(
        Long messageId,
        Long roomId,
        Long senderId,
        String senderNickname,
        ChatMessageType type,
        String content,
        Instant createdAt,
        Instant editedAt,
        boolean deleted
) {
    public static ChatMessageResponse from(ChatMessage message) {
        Member sender = message.getSender();
        boolean deleted = message.getDeletedAt() != null;

        return new ChatMessageResponse(
                message.getId(),
                message.getChatRoom().getId(),
                sender != null ? sender.getId() : null,
                sender != null ? sender.getNickname() : null,
                message.getType(),
                deleted ? null : message.getContent(),
                message.getCreatedAt(),
                message.getEditedAt(),
                deleted
        );
    }
}
