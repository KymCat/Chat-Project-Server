package com.project.ChatProject.service;

import com.project.ChatProject.entity.Attachment;
import com.project.ChatProject.entity.ChatRoom;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.enums.AttachmentStatus;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.repository.AttachmentRepository;
import com.project.ChatProject.storage.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentCleanupServiceTest {

    private static final Long ATTACHMENT_ID = 100L;
    private static final String STORAGE_KEY =
            "2026/09/28/storage-key";

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private FileStorage fileStorage;

    private AttachmentCleanupService attachmentCleanupService;

    @BeforeEach
    void setUp() {
        attachmentCleanupService = new AttachmentCleanupService(
                attachmentRepository,
                fileStorage
        );
    }

    @Test
    void cleanupPendingAttachmentDeletesExpiredPendingAttachment() {
        Instant cutoff = Instant.parse("2026-09-29T00:00:00Z");
        Attachment attachment = pendingAttachment(
                cutoff.minusSeconds(1)
        );
        when(attachmentRepository.findByForUpdate(ATTACHMENT_ID))
                .thenReturn(Optional.of(attachment));

        attachmentCleanupService.cleanupPendingAttachment(
                ATTACHMENT_ID,
                cutoff
        );

        verify(fileStorage).delete(STORAGE_KEY);
        assertThat(attachment.getStatus())
                .isEqualTo(AttachmentStatus.DELETED);
        assertThat(attachment.getDeletedAt()).isNotNull();
    }

    @Test
    void cleanupPendingAttachmentReturnsWhenAttachmentDoesNotExist() {
        Instant cutoff = Instant.parse("2026-09-29T00:00:00Z");
        when(attachmentRepository.findByForUpdate(ATTACHMENT_ID))
                .thenReturn(Optional.empty());

        attachmentCleanupService.cleanupPendingAttachment(
                ATTACHMENT_ID,
                cutoff
        );

        verifyNoInteractions(fileStorage);
    }

    @Test
    void cleanupPendingAttachmentReturnsWhenAttachmentIsActive() {
        Instant cutoff = Instant.parse("2026-09-29T00:00:00Z");
        Attachment attachment = pendingAttachment(
                cutoff.minusSeconds(1)
        );
        attachment.activate();
        when(attachmentRepository.findByForUpdate(ATTACHMENT_ID))
                .thenReturn(Optional.of(attachment));

        attachmentCleanupService.cleanupPendingAttachment(
                ATTACHMENT_ID,
                cutoff
        );

        verifyNoInteractions(fileStorage);
        assertThat(attachment.getStatus())
                .isEqualTo(AttachmentStatus.ACTIVE);
        assertThat(attachment.getDeletedAt()).isNull();
    }

    @Test
    void cleanupPendingAttachmentReturnsWhenCreatedAtEqualsCutoff() {
        Instant cutoff = Instant.parse("2026-09-29T00:00:00Z");
        Attachment attachment = pendingAttachment(cutoff);
        when(attachmentRepository.findByForUpdate(ATTACHMENT_ID))
                .thenReturn(Optional.of(attachment));

        attachmentCleanupService.cleanupPendingAttachment(
                ATTACHMENT_ID,
                cutoff
        );

        verifyNoInteractions(fileStorage);
        assertThat(attachment.getStatus())
                .isEqualTo(AttachmentStatus.PENDING);
        assertThat(attachment.getDeletedAt()).isNull();
    }

    @Test
    void cleanupPendingAttachmentDoesNotChangeStatusWhenFileDeleteFails() {
        Instant cutoff = Instant.parse("2026-09-29T00:00:00Z");
        Attachment attachment = pendingAttachment(
                cutoff.minusSeconds(1)
        );
        CustomException failure =
                new CustomException(ErrorCode.ATTACHMENT_STORAGE_FAILED);
        when(attachmentRepository.findByForUpdate(ATTACHMENT_ID))
                .thenReturn(Optional.of(attachment));
        doThrow(failure).when(fileStorage).delete(STORAGE_KEY);

        assertThatThrownBy(() ->
                attachmentCleanupService.cleanupPendingAttachment(
                        ATTACHMENT_ID,
                        cutoff
                )
        ).isSameAs(failure);

        assertThat(attachment.getStatus())
                .isEqualTo(AttachmentStatus.PENDING);
        assertThat(attachment.getDeletedAt()).isNull();
        verify(fileStorage).delete(STORAGE_KEY);
    }

    private Attachment pendingAttachment(Instant createdAt) {
        Member uploader = Member.create(
                null,
                "user@example.com",
                "사용자"
        );
        ChatRoom chatRoom = ChatRoom.create("Backend");
        ReflectionTestUtils.setField(chatRoom, "id", 10L);
        Attachment attachment = Attachment.createPending(
                uploader,
                chatRoom,
                "photo.png",
                "image/png",
                13L,
                STORAGE_KEY
        );
        ReflectionTestUtils.setField(
                attachment,
                "id",
                ATTACHMENT_ID
        );
        ReflectionTestUtils.setField(
                attachment,
                "createdAt",
                createdAt
        );
        return attachment;
    }
}
