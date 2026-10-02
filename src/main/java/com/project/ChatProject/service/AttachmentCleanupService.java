package com.project.ChatProject.service;

import com.project.ChatProject.entity.Attachment;
import com.project.ChatProject.entity.enums.AttachmentStatus;
import com.project.ChatProject.repository.AttachmentRepository;
import com.project.ChatProject.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AttachmentCleanupService {

    private final AttachmentRepository attachmentRepository;
    private final FileStorage fileStorage;

    @Transactional
    public void cleanupPendingAttachment(
            Long attachmentId,
            Instant cutoff
    ) {
        Attachment attachment = attachmentRepository
                .findByForUpdate(attachmentId)
                .orElse(null);

        if (attachment == null) {
            return;
        }

        if (!attachment.getStatus().equals(AttachmentStatus.PENDING)) {
            return;
        }

        if (!attachment.getCreatedAt().isBefore(cutoff)) {
            return;
        }

        fileStorage.delete(attachment.getStorageKey());
        attachment.delete();
    }
}
