package com.project.ChatProject.service;

import com.project.ChatProject.attachment.AttachmentValidator;
import com.project.ChatProject.attachment.AttachmentUploadRateLimiter;
import com.project.ChatProject.attachment.ValidatedAttachment;
import com.project.ChatProject.dto.result.AttachmentDownloadResult;
import com.project.ChatProject.dto.response.AttachmentUploadResponse;
import com.project.ChatProject.entity.Attachment;
import com.project.ChatProject.entity.ChatMessage;
import com.project.ChatProject.entity.ChatRoom;
import com.project.ChatProject.entity.ChatRoomMember;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.enums.AttachmentStatus;
import com.project.ChatProject.entity.enums.ChatMessageType;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.repository.AttachmentRepository;
import com.project.ChatProject.repository.ChatMessageRepository;
import com.project.ChatProject.service.support.ChatRoomParticipationContext;
import com.project.ChatProject.service.support.ParticipationLockMode;
import com.project.ChatProject.storage.FileStorage;
import com.project.ChatProject.storage.StoredFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    private static final Long ROOM_ID = 10L;
    private static final Long MEMBER_ID = 1L;

    @Mock
    private ChatRoomParticipationService participation;

    @Mock
    private AttachmentValidator attachmentValidator;

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private AttachmentUploadRateLimiter uploadRateLimiter;

    @Mock
    private FileStorage fileStorage;

    private AttachmentService attachmentService;
    private MockMultipartFile file;
    private ChatRoomParticipationContext context;

    @BeforeEach
    void setUp() {
        attachmentService = new AttachmentService(
                participation,
                attachmentValidator,
                attachmentRepository,
                chatMessageRepository,
                uploadRateLimiter,
                fileStorage
        );

        file = new MockMultipartFile(
                "file",
                "photo.png",
                "image/png;charset=UTF-8",
                "image-content".getBytes()
        );

        Member member = Member.create(
                null,
                "user@example.com",
                "사용자"
        );
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        ChatRoom chatRoom = ChatRoom.create("Backend");
        ReflectionTestUtils.setField(chatRoom, "id", ROOM_ID);
        ChatRoomMember chatRoomMember =
                ChatRoomMember.createMember(chatRoom, member);
        context = new ChatRoomParticipationContext(
                chatRoom,
                member,
                chatRoomMember
        );
    }

    @Test
    void uploadStoresPendingAttachmentWithValidatedMetadata() {
        ValidatedAttachment validated = new ValidatedAttachment(
                ChatMessageType.IMAGE,
                "image/png"
        );
        StoredFile storedFile = new StoredFile(
                "2026/09/28/storage-key",
                "photo.png",
                "image/png;charset=UTF-8",
                file.getSize()
        );
        stubBeforeDatabaseSave(validated, storedFile);
        when(attachmentRepository.saveAndFlush(
                org.mockito.ArgumentMatchers.any(Attachment.class)
        )).thenAnswer(invocation -> {
            Attachment attachment = invocation.getArgument(0);
            ReflectionTestUtils.setField(attachment, "id", 100L);
            return attachment;
        });

        AttachmentUploadResponse response = attachmentService.upload(
                ROOM_ID,
                file,
                MEMBER_ID
        );

        ArgumentCaptor<Attachment> captor =
                ArgumentCaptor.forClass(Attachment.class);
        verify(attachmentRepository).saveAndFlush(captor.capture());
        Attachment savedAttachment = captor.getValue();

        assertThat(savedAttachment.getId()).isEqualTo(100L);
        assertThat(savedAttachment.getUploader()).isSameAs(context.member());
        assertThat(savedAttachment.getChatRoom())
                .isSameAs(context.chatRoom());
        assertThat(savedAttachment.getOriginalName()).isEqualTo("photo.png");
        assertThat(savedAttachment.getContentType()).isEqualTo("image/png");
        assertThat(savedAttachment.getSizeBytes()).isEqualTo(file.getSize());
        assertThat(savedAttachment.getStorageKey())
                .isEqualTo("2026/09/28/storage-key");
        assertThat(savedAttachment.getStatus())
                .isEqualTo(AttachmentStatus.PENDING);
        assertThat(savedAttachment.getActivatedAt()).isNull();
        assertThat(savedAttachment.getDeletedAt()).isNull();

        assertThat(response).isEqualTo(new AttachmentUploadResponse(
                100L,
                ChatMessageType.IMAGE,
                "photo.png",
                "image/png",
                file.getSize()
        ));
        verify(uploadRateLimiter).checkAllowed(MEMBER_ID);
        verify(fileStorage, never()).delete(storedFile.storageKey());
    }

    @Test
    void uploadStopsWhenParticipationValidationFails() {
        CustomException failure =
                new CustomException(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
        when(participation.requireParticipation(
                ROOM_ID,
                MEMBER_ID,
                ParticipationLockMode.NONE
        )).thenThrow(failure);

        assertThatThrownBy(() -> attachmentService.upload(
                ROOM_ID,
                file,
                MEMBER_ID
        )).isSameAs(failure);

        verifyNoInteractions(
                uploadRateLimiter,
                attachmentValidator,
                fileStorage,
                attachmentRepository
        );
    }

    @Test
    void uploadStopsBeforeValidationWhenRateLimitIsExceeded() {
        CustomException failure = new CustomException(
                ErrorCode.ATTACHMENT_UPLOAD_TOO_MANY_REQUESTS
        );
        when(participation.requireParticipation(
                ROOM_ID,
                MEMBER_ID,
                ParticipationLockMode.NONE
        )).thenReturn(context);
        doThrow(failure)
                .when(uploadRateLimiter)
                .checkAllowed(MEMBER_ID);

        assertThatThrownBy(() -> attachmentService.upload(
                ROOM_ID,
                file,
                MEMBER_ID
        )).isSameAs(failure);

        verifyNoInteractions(
                attachmentValidator,
                fileStorage,
                attachmentRepository
        );
    }

    @Test
    void uploadStopsBeforeStorageWhenAttachmentValidationFails() {
        CustomException failure =
                new CustomException(ErrorCode.ATTACHMENT_TYPE_NOT_ALLOWED);
        when(participation.requireParticipation(
                ROOM_ID,
                MEMBER_ID,
                ParticipationLockMode.NONE
        )).thenReturn(context);
        when(attachmentValidator.validate(file)).thenThrow(failure);

        assertThatThrownBy(() -> attachmentService.upload(
                ROOM_ID,
                file,
                MEMBER_ID
        )).isSameAs(failure);

        verifyNoInteractions(fileStorage, attachmentRepository);
    }

    @Test
    void uploadStopsBeforeDatabaseWhenStorageFails() {
        ValidatedAttachment validated = new ValidatedAttachment(
                ChatMessageType.IMAGE,
                "image/png"
        );
        CustomException failure =
                new CustomException(ErrorCode.ATTACHMENT_STORAGE_FAILED);
        when(participation.requireParticipation(
                ROOM_ID,
                MEMBER_ID,
                ParticipationLockMode.NONE
        )).thenReturn(context);
        when(attachmentValidator.validate(file)).thenReturn(validated);
        when(fileStorage.store(file)).thenThrow(failure);

        assertThatThrownBy(() -> attachmentService.upload(
                ROOM_ID,
                file,
                MEMBER_ID
        )).isSameAs(failure);

        verifyNoInteractions(attachmentRepository);
    }

    @Test
    void uploadDeletesStoredFileWhenDatabaseSaveFails() {
        ValidatedAttachment validated = new ValidatedAttachment(
                ChatMessageType.FILE,
                "application/pdf"
        );
        StoredFile storedFile = new StoredFile(
                "2026/09/28/storage-key",
                "document.pdf",
                "application/pdf",
                file.getSize()
        );
        DataIntegrityViolationException failure =
                new DataIntegrityViolationException("DB 저장 실패");
        stubBeforeDatabaseSave(validated, storedFile);
        when(attachmentRepository.saveAndFlush(
                org.mockito.ArgumentMatchers.any(Attachment.class)
        )).thenThrow(failure);

        assertThatThrownBy(() -> attachmentService.upload(
                ROOM_ID,
                file,
                MEMBER_ID
        )).isSameAs(failure);

        verify(fileStorage).delete(storedFile.storageKey());
    }

    @Test
    void uploadPreservesCleanupFailureAsSuppressedException() {
        ValidatedAttachment validated = new ValidatedAttachment(
                ChatMessageType.FILE,
                "application/pdf"
        );
        StoredFile storedFile = new StoredFile(
                "2026/09/28/storage-key",
                "document.pdf",
                "application/pdf",
                file.getSize()
        );
        DataIntegrityViolationException databaseFailure =
                new DataIntegrityViolationException("DB 저장 실패");
        CustomException cleanupFailure =
                new CustomException(ErrorCode.ATTACHMENT_STORAGE_FAILED);
        stubBeforeDatabaseSave(validated, storedFile);
        when(attachmentRepository.saveAndFlush(
                org.mockito.ArgumentMatchers.any(Attachment.class)
        )).thenThrow(databaseFailure);
        doThrow(cleanupFailure)
                .when(fileStorage)
                .delete(storedFile.storageKey());

        assertThatThrownBy(() -> attachmentService.upload(
                ROOM_ID,
                file,
                MEMBER_ID
        )).isSameAs(databaseFailure)
                .satisfies(exception ->
                        assertThat(exception.getSuppressed())
                                .containsExactly(cleanupFailure)
                );
    }

    @Test
    void downloadReturnsActiveAttachmentResource() {
        Attachment attachment = activeAttachment();
        ChatMessage message = ChatMessage.createAttachment(
                context.chatRoom(),
                context.member(),
                ChatMessageType.IMAGE,
                attachment
        );
        Resource resource = new ByteArrayResource("image-content".getBytes());

        when(chatMessageRepository
                .findActiveMessageByRoomIdAndAttachmentId(ROOM_ID, 100L))
                .thenReturn(java.util.Optional.of(message));
        when(fileStorage.load(attachment.getStorageKey()))
                .thenReturn(resource);

        AttachmentDownloadResult result = attachmentService.download(
                ROOM_ID,
                100L,
                MEMBER_ID
        );

        assertThat(result.resource()).isSameAs(resource);
        assertThat(result.originalName()).isEqualTo("photo.png");
        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(result.sizeBytes()).isEqualTo(13L);
        assertThat(result.contentDisposition()).startsWith("inline;");
        verify(participation).requireParticipation(
                ROOM_ID,
                MEMBER_ID,
                ParticipationLockMode.NONE
        );
        verify(fileStorage).load("2026/09/28/storage-key");
    }

    @Test
    void downloadStopsWhenParticipationValidationFails() {
        CustomException failure =
                new CustomException(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
        when(participation.requireParticipation(
                ROOM_ID,
                MEMBER_ID,
                ParticipationLockMode.NONE
        )).thenThrow(failure);

        assertThatThrownBy(() -> attachmentService.download(
                ROOM_ID,
                100L,
                MEMBER_ID
        )).isSameAs(failure);

        verifyNoInteractions(chatMessageRepository, fileStorage);
    }

    @Test
    void downloadRejectsAttachmentNotConnectedToRoomMessage() {
        when(chatMessageRepository
                .findActiveMessageByRoomIdAndAttachmentId(ROOM_ID, 100L))
                .thenReturn(java.util.Optional.empty());

        assertDownloadRejected(ErrorCode.ATTACHMENT_NOT_FOUND);

        verifyNoInteractions(fileStorage);
    }

    @Test
    void downloadRejectsPendingAttachment() {
        Attachment attachment = pendingAttachment();
        ChatMessage message = ChatMessage.createAttachment(
                context.chatRoom(),
                context.member(),
                ChatMessageType.IMAGE,
                attachment
        );
        when(chatMessageRepository
                .findActiveMessageByRoomIdAndAttachmentId(ROOM_ID, 100L))
                .thenReturn(java.util.Optional.of(message));

        assertDownloadRejected(ErrorCode.ATTACHMENT_NOT_FOUND);

        verifyNoInteractions(fileStorage);
    }

    @Test
    void downloadPropagatesMissingStoredFileFailure() {
        Attachment attachment = activeAttachment();
        ChatMessage message = ChatMessage.createAttachment(
                context.chatRoom(),
                context.member(),
                ChatMessageType.IMAGE,
                attachment
        );
        CustomException failure =
                new CustomException(ErrorCode.ATTACHMENT_NOT_FOUND);
        when(chatMessageRepository
                .findActiveMessageByRoomIdAndAttachmentId(ROOM_ID, 100L))
                .thenReturn(java.util.Optional.of(message));
        when(fileStorage.load(attachment.getStorageKey()))
                .thenThrow(failure);

        assertThatThrownBy(() -> attachmentService.download(
                ROOM_ID,
                100L,
                MEMBER_ID
        )).isSameAs(failure);
    }

    private void stubBeforeDatabaseSave(
            ValidatedAttachment validated,
            StoredFile storedFile
    ) {
        when(participation.requireParticipation(
                ROOM_ID,
                MEMBER_ID,
                ParticipationLockMode.NONE
        )).thenReturn(context);
        when(attachmentValidator.validate(file)).thenReturn(validated);
        when(fileStorage.store(file)).thenReturn(storedFile);
    }

    private void assertDownloadRejected(ErrorCode expectedErrorCode) {
        assertThatThrownBy(() -> attachmentService.download(
                ROOM_ID,
                100L,
                MEMBER_ID
        )).isInstanceOfSatisfying(
                CustomException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(expectedErrorCode)
        );
    }

    private Attachment activeAttachment() {
        Attachment attachment = pendingAttachment();
        attachment.activate();
        return attachment;
    }

    private Attachment pendingAttachment() {
        Attachment attachment = Attachment.createPending(
                context.member(),
                context.chatRoom(),
                "photo.png",
                "image/png",
                13L,
                "2026/09/28/storage-key"
        );
        ReflectionTestUtils.setField(attachment, "id", 100L);
        return attachment;
    }
}
