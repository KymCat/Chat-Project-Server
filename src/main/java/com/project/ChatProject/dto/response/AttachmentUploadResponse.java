package com.project.ChatProject.dto.response;

import com.project.ChatProject.entity.Attachment;
import com.project.ChatProject.entity.enums.ChatMessageType;

public record AttachmentUploadResponse(
        Long attachmentId,
        ChatMessageType messageType,
        String originalName,
        String contentType,
        long sizeBytes
) {
    public static AttachmentUploadResponse from(
            Attachment attachment,
            ChatMessageType messageType
    ) {
        return new AttachmentUploadResponse(
                attachment.getId(),
                messageType,
                attachment.getOriginalName(),
                attachment.getContentType(),
                attachment.getSizeBytes()
        );
    }
}
