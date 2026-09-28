package com.project.ChatProject.service;

import com.project.ChatProject.attachment.AttachmentValidator;
import com.project.ChatProject.attachment.ValidatedAttachment;
import com.project.ChatProject.dto.response.AttachmentUploadResponse;
import com.project.ChatProject.entity.Attachment;
import com.project.ChatProject.repository.AttachmentRepository;
import com.project.ChatProject.service.support.ChatRoomParticipationContext;
import com.project.ChatProject.service.support.ParticipationLockMode;
import com.project.ChatProject.storage.FileStorage;
import com.project.ChatProject.storage.StoredFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class AttachmentService {

    private final ChatRoomParticipationService participation;
    private final AttachmentValidator attachmentValidator;
    private final AttachmentRepository attachmentRepository;
    private final FileStorage fileStorage;

    @Transactional
    public AttachmentUploadResponse upload(
            Long roomId,
            MultipartFile file,
            Long memberId
    )
    {
        ChatRoomParticipationContext context =
                participation.requireParticipation(
                        roomId,
                        memberId,
                        ParticipationLockMode.NONE
        );

        ValidatedAttachment validated =
                attachmentValidator.validate(file);

        StoredFile storedFile = fileStorage.store(file);

        try {
            Attachment attachment = Attachment.createPending(
                    context.member(),
                    storedFile.originalName(),
                    validated.contentType(),
                    storedFile.sizeBytes(),
                    storedFile.storageKey()
            );

            attachmentRepository.saveAndFlush(attachment);
            return AttachmentUploadResponse
                    .from(
                            attachment,
                            validated.messageType()
                    );
        } catch (RuntimeException exception) {
            try {
                fileStorage.delete(storedFile.storageKey());
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }
}
