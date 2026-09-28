package com.project.ChatProject.dto.result;

import com.project.ChatProject.entity.Attachment;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;

import java.nio.charset.StandardCharsets;

public record AttachmentDownloadResult(
        Resource resource,
        String originalName,
        String contentType,
        long sizeBytes
) {

    public static AttachmentDownloadResult from(
            Attachment attachment,
            Resource resource
    ) {
        return new AttachmentDownloadResult(
                resource,
                attachment.getOriginalName(),
                attachment.getContentType(),
                attachment.getSizeBytes()
        );
    }

    public String contentDisposition() {
        ContentDisposition.Builder builder =
                contentType.startsWith("image/")
                        ? ContentDisposition.inline()
                        : ContentDisposition.attachment();

        return builder
                .filename(originalName, StandardCharsets.UTF_8)
                .build()
                .toString();
    }
}
