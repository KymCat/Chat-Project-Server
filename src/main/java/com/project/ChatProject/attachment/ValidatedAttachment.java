package com.project.ChatProject.attachment;

import com.project.ChatProject.entity.enums.ChatMessageType;

public record ValidatedAttachment(
        ChatMessageType messageType,
        String contentType // Validator에서 정규화하고 검증한 값
) {
}
