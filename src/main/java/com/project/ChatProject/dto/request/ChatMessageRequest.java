package com.project.ChatProject.dto.request;

public record ChatMessageRequest(
        String content,
        String roomId
) {
}
