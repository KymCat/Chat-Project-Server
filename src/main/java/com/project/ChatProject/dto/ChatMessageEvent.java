package com.project.ChatProject.dto;

import com.project.ChatProject.dto.response.ChatMessageResponse;
import com.project.ChatProject.entity.enums.ChatMessageEventType;

public record ChatMessageEvent(
        ChatMessageEventType eventType,
        ChatMessageResponse message
) {

    public static ChatMessageEvent created(ChatMessageResponse message) {
        return new ChatMessageEvent(
                ChatMessageEventType.CREATED,
                message
        );
    }

    public static ChatMessageEvent updated(ChatMessageResponse message) {
        return new ChatMessageEvent(
                ChatMessageEventType.UPDATED,
                message
        );
    }

    public static ChatMessageEvent deleted(ChatMessageResponse message) {
        return new ChatMessageEvent(
                ChatMessageEventType.DELETED,
                message
        );
    }
}
