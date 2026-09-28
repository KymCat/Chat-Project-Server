package com.project.ChatProject.dto.response;

import com.project.ChatProject.entity.Attachment;

public record AttachmentResponse(
        Long attachmentId,
        String originalName,
        String contentType,
        long sizeBytes
) {
    public static AttachmentResponse from(Attachment attachment) {
        return new AttachmentResponse(
                attachment.getId(),
                attachment.getOriginalName(),
                attachment.getContentType(),
                attachment.getSizeBytes()
        );
    }
}
