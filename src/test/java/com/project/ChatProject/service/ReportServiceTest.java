package com.project.ChatProject.service;

import com.project.ChatProject.dto.request.MessageReportRequest;
import com.project.ChatProject.dto.response.MessageReportDetailResponse;
import com.project.ChatProject.dto.response.MessageReportListResponse;
import com.project.ChatProject.dto.result.AttachmentDownloadResult;
import com.project.ChatProject.entity.Attachment;
import com.project.ChatProject.entity.ChatMessage;
import com.project.ChatProject.entity.ChatRoom;
import com.project.ChatProject.entity.ChatRoomMember;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.MessageReport;
import com.project.ChatProject.entity.enums.ChatMessageType;
import com.project.ChatProject.entity.enums.AttachmentStatus;
import com.project.ChatProject.entity.enums.MemberStatus;
import com.project.ChatProject.entity.enums.MessageReportReason;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.jwt.refresh.RefreshTokenStore;
import com.project.ChatProject.repository.ChatMessageRepository;
import com.project.ChatProject.repository.MessageReportRepository;
import com.project.ChatProject.service.support.ChatRoomParticipationContext;
import com.project.ChatProject.service.support.ParticipationLockMode;
import com.project.ChatProject.storage.FileStorage;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedModel;
import org.springframework.core.io.Resource;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final Long REPORTER_ID = 1L;
    private static final Long REPORTED_MEMBER_ID = 2L;
    private static final Long ADMIN_ID = 3L;
    private static final Long REPORT_ID = 200L;
    private static final Long ROOM_ID = 10L;
    private static final Long MESSAGE_ID = 100L;
    private static final Instant JOINED_AT =
            Instant.parse("2026-11-20T00:00:00Z");
    private static final Instant MESSAGE_CREATED_AT =
            Instant.parse("2026-11-20T00:01:00Z");

    @Mock
    private AdministratorService administratorService;

    @Mock
    private MessageReportRepository messageReportRepository;

    @Mock
    private ChatRoomParticipationService participationService;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @Mock
    private FileStorage fileStorage;

    @InjectMocks
    private ReportService reportService;

    @Test
    void reportStoresReporterSenderAndMessageSnapshot() {
        ReportFixture fixture = reportableMessage(
                ChatMessageType.TEXT,
                "신고 당시 메시지",
                MESSAGE_CREATED_AT
        );
        MessageReportRequest request = new MessageReportRequest(
                MessageReportReason.ABUSE,
                null
        );
        when(messageReportRepository.existsByReporterIdAndMessageId(
                REPORTER_ID,
                MESSAGE_ID
        )).thenReturn(false);

        reportService.report(
                REPORTER_ID,
                ROOM_ID,
                MESSAGE_ID,
                request
        );

        ArgumentCaptor<MessageReport> captor =
                ArgumentCaptor.forClass(MessageReport.class);
        verify(messageReportRepository).saveAndFlush(captor.capture());

        MessageReport savedReport = captor.getValue();
        assertThat(savedReport.getReporter()).isSameAs(fixture.reporter());
        assertThat(savedReport.getReportedMember()).isSameAs(fixture.sender());
        assertThat(savedReport.getMessage()).isSameAs(fixture.message());
        assertThat(savedReport.getReason()).isEqualTo(MessageReportReason.ABUSE);
        assertThat(savedReport.getDetail()).isNull();
        assertThat(savedReport.getMessageTypeSnapshot())
                .isEqualTo(ChatMessageType.TEXT);
        assertThat(savedReport.getMessageContentSnapshot())
                .isEqualTo("신고 당시 메시지");
    }

    @Test
    void reportStopsWhenReporterParticipationValidationFails() {
        CustomException failure =
                new CustomException(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
        when(participationService.requireConversationParticipation(
                ROOM_ID,
                REPORTER_ID,
                ParticipationLockMode.NONE
        )).thenThrow(failure);

        assertThatThrownBy(() -> reportService.report(
                REPORTER_ID,
                ROOM_ID,
                MESSAGE_ID,
                request()
        )).isSameAs(failure);

        verifyNoInteractions(chatMessageRepository, messageReportRepository);
    }

    @Test
    void reportRejectsMessageThatReporterCannotSee() {
        stubParticipation();
        when(chatMessageRepository.findVisibleByIdAndChatRoomId(
                MESSAGE_ID,
                ROOM_ID,
                REPORTER_ID
        )).thenReturn(Optional.empty());

        assertErrorCode(
                () -> reportService.report(
                        REPORTER_ID,
                        ROOM_ID,
                        MESSAGE_ID,
                        request()
                ),
                ErrorCode.CHAT_MESSAGE_NOT_FOUND
        );

        verify(messageReportRepository, never())
                .existsByReporterIdAndMessageId(any(), any());
        verify(messageReportRepository, never())
                .saveAndFlush(any(MessageReport.class));
    }

    @Test
    void reportRejectsSystemMessage() {
        reportableMessage(
                ChatMessageType.SYSTEM,
                "시스템 메시지",
                MESSAGE_CREATED_AT
        );

        assertReportRejected(ErrorCode.REPORT_DENIED);
    }

    @Test
    void reportRejectsDeletedMessage() {
        ReportFixture fixture = reportableMessage(
                ChatMessageType.TEXT,
                "삭제된 메시지",
                MESSAGE_CREATED_AT
        );
        when(fixture.message().getDeletedAt()).thenReturn(Instant.now());

        assertReportRejected(ErrorCode.REPORT_DENIED);
    }

    @Test
    void reportRejectsOwnMessage() {
        ReportFixture fixture = reportableMessage(
                ChatMessageType.TEXT,
                "내 메시지",
                MESSAGE_CREATED_AT
        );
        when(fixture.sender().getId()).thenReturn(REPORTER_ID);

        assertReportRejected(ErrorCode.REPORT_MEMBER_ID_SAME);
    }

    @Test
    void reportRejectsMessageCreatedBeforeCurrentParticipation() {
        reportableMessage(
                ChatMessageType.TEXT,
                "참여 전 메시지",
                JOINED_AT.minusSeconds(1)
        );

        assertReportRejected(ErrorCode.CHAT_MESSAGE_NOT_FOUND);
    }

    @Test
    void reportRejectsExistingReport() {
        reportableMessage(
                ChatMessageType.TEXT,
                "중복 신고 메시지",
                MESSAGE_CREATED_AT
        );
        when(messageReportRepository.existsByReporterIdAndMessageId(
                REPORTER_ID,
                MESSAGE_ID
        )).thenReturn(true);

        assertReportRejected(ErrorCode.REPORT_ALREADY_EXISTS);

        verify(messageReportRepository, never())
                .saveAndFlush(any(MessageReport.class));
    }

    @Test
    void reportConvertsDuplicateConstraintViolationToConflict() {
        reportableMessage(
                ChatMessageType.TEXT,
                "동시 중복 신고 메시지",
                MESSAGE_CREATED_AT
        );
        when(messageReportRepository.existsByReporterIdAndMessageId(
                REPORTER_ID,
                MESSAGE_ID
        )).thenReturn(false);
        when(messageReportRepository.saveAndFlush(any(MessageReport.class)))
                .thenThrow(databaseFailure(
                        "uq_message_reports_reporter_id_message_id"
                ));

        assertReportRejected(ErrorCode.REPORT_ALREADY_EXISTS);
    }

    @Test
    void reportRethrowsUnrelatedDatabaseIntegrityViolation() {
        reportableMessage(
                ChatMessageType.TEXT,
                "DB 실패 메시지",
                MESSAGE_CREATED_AT
        );
        when(messageReportRepository.existsByReporterIdAndMessageId(
                REPORTER_ID,
                MESSAGE_ID
        )).thenReturn(false);
        DataIntegrityViolationException failure =
                databaseFailure("another_constraint");
        when(messageReportRepository.saveAndFlush(any(MessageReport.class)))
                .thenThrow(failure);

        assertThatThrownBy(() -> reportService.report(
                REPORTER_ID,
                ROOM_ID,
                MESSAGE_ID,
                request()
        )).isSameAs(failure);
    }

    @Test
    void getMessageReportsReturnsMappedPage() {
        Member reporter = org.mockito.Mockito.mock(Member.class);
        Member reportedMember = org.mockito.Mockito.mock(Member.class);
        ChatMessage message = org.mockito.Mockito.mock(ChatMessage.class);
        MessageReport report = org.mockito.Mockito.mock(MessageReport.class);
        Instant reportedAt = Instant.parse("2026-11-23T00:00:00Z");
        PageRequest pageable = PageRequest.of(2, 20);

        when(report.getId()).thenReturn(REPORT_ID);
        when(report.getReason()).thenReturn(MessageReportReason.SPAM);
        when(report.getReporter()).thenReturn(reporter);
        when(report.getReportedMember()).thenReturn(reportedMember);
        when(report.getMessage()).thenReturn(message);
        when(report.getCreatedAt()).thenReturn(reportedAt);
        when(reporter.getId()).thenReturn(REPORTER_ID);
        when(reporter.getNickname()).thenReturn("신고자");
        when(reportedMember.getId()).thenReturn(REPORTED_MEMBER_ID);
        when(reportedMember.getNickname()).thenReturn("신고 대상");
        when(reportedMember.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(message.getId()).thenReturn(MESSAGE_ID);
        when(messageReportRepository.findAllMessageReport(pageable))
                .thenReturn(new PageImpl<>(
                        List.of(report),
                        pageable,
                        41
                ));

        PagedModel<MessageReportListResponse> result =
                reportService.getMessageReports(ADMIN_ID, 2);

        assertThat(result.getContent())
                .singleElement()
                .satisfies(response -> {
                    assertThat(response.reportId()).isEqualTo(REPORT_ID);
                    assertThat(response.reason())
                            .isEqualTo(MessageReportReason.SPAM);
                    assertThat(response.reporterId())
                            .isEqualTo(REPORTER_ID);
                    assertThat(response.reportedMemberId())
                            .isEqualTo(REPORTED_MEMBER_ID);
                    assertThat(response.messageId()).isEqualTo(MESSAGE_ID);
                    assertThat(response.createdAt()).isEqualTo(reportedAt);
                });
        assertThat(result.getMetadata().number()).isEqualTo(2);
        assertThat(result.getMetadata().size()).isEqualTo(20);
        assertThat(result.getMetadata().totalElements()).isEqualTo(41);
        verify(administratorService).requireAccess(ADMIN_ID);
        verify(messageReportRepository).findAllMessageReport(pageable);
    }

    @Test
    void getMessageReportsStopsWhenAdministratorAccessIsDenied() {
        CustomException failure =
                new CustomException(ErrorCode.ADMIN_ACCESS_DENIED);
        doThrow(failure)
                .when(administratorService)
                .requireAccess(ADMIN_ID);

        assertThatThrownBy(() ->
                reportService.getMessageReports(ADMIN_ID, 0)
        ).isSameAs(failure);

        verifyNoInteractions(messageReportRepository);
    }

    @Test
    void getMessageReportReturnsMappedDetail() {
        Member reporter = org.mockito.Mockito.mock(Member.class);
        Member reportedMember = org.mockito.Mockito.mock(Member.class);
        ChatRoom chatRoom = org.mockito.Mockito.mock(ChatRoom.class);
        ChatMessage message = org.mockito.Mockito.mock(ChatMessage.class);
        MessageReport report = org.mockito.Mockito.mock(MessageReport.class);
        Instant reportedAt = Instant.parse("2026-11-23T00:00:00Z");
        Instant messageCreatedAt =
                Instant.parse("2026-11-22T23:59:00Z");

        when(messageReportRepository.findDetailById(REPORT_ID))
                .thenReturn(Optional.of(report));
        when(report.getId()).thenReturn(REPORT_ID);
        when(report.getReason()).thenReturn(MessageReportReason.ABUSE);
        when(report.getDetail()).thenReturn("상세 사유");
        when(report.getCreatedAt()).thenReturn(reportedAt);
        when(report.getReporter()).thenReturn(reporter);
        when(report.getReportedMember()).thenReturn(reportedMember);
        when(report.getMessage()).thenReturn(message);
        when(report.getMessageTypeSnapshot()).thenReturn(ChatMessageType.TEXT);
        when(report.getMessageContentSnapshot()).thenReturn("신고 메시지");
        when(reporter.getId()).thenReturn(REPORTER_ID);
        when(reporter.getNickname()).thenReturn("신고자");
        when(reportedMember.getId()).thenReturn(REPORTED_MEMBER_ID);
        when(reportedMember.getEmail()).thenReturn("reported@example.com");
        when(reportedMember.getNickname()).thenReturn("신고 대상");
        when(reportedMember.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(message.getChatRoom()).thenReturn(chatRoom);
        when(chatRoom.getId()).thenReturn(ROOM_ID);
        when(message.getId()).thenReturn(MESSAGE_ID);
        when(message.getCreatedAt()).thenReturn(messageCreatedAt);

        MessageReportDetailResponse result =
                reportService.getMessageReport(ADMIN_ID, REPORT_ID);

        assertThat(result.reportId()).isEqualTo(REPORT_ID);
        assertThat(result.reason()).isEqualTo(MessageReportReason.ABUSE);
        assertThat(result.details()).isEqualTo("상세 사유");
        assertThat(result.reportedAt()).isEqualTo(reportedAt);
        assertThat(result.reporterId()).isEqualTo(REPORTER_ID);
        assertThat(result.reportedMemberId()).isEqualTo(REPORTED_MEMBER_ID);
        assertThat(result.roomId()).isEqualTo(ROOM_ID);
        assertThat(result.messageId()).isEqualTo(MESSAGE_ID);
        assertThat(result.messageType()).isEqualTo(ChatMessageType.TEXT);
        assertThat(result.messageContent()).isEqualTo("신고 메시지");
        assertThat(result.messageCreatedAt()).isEqualTo(messageCreatedAt);
        assertThat(result.messageDeleted()).isFalse();
        verify(administratorService).requireAccess(ADMIN_ID);
    }

    @Test
    void getMessageReportRejectsMissingReport() {
        when(messageReportRepository.findDetailById(REPORT_ID))
                .thenReturn(Optional.empty());

        assertErrorCode(
                () -> reportService.getMessageReport(ADMIN_ID, REPORT_ID),
                ErrorCode.REPORT_NOT_FOUND
        );
    }

    @Test
    void getMessageReportStopsWhenAdministratorAccessIsDenied() {
        CustomException failure =
                new CustomException(ErrorCode.ADMIN_ACCESS_DENIED);
        doThrow(failure)
                .when(administratorService)
                .requireAccess(ADMIN_ID);

        assertThatThrownBy(() ->
                reportService.getMessageReport(ADMIN_ID, REPORT_ID)
        ).isSameAs(failure);

        verifyNoInteractions(messageReportRepository);
    }

    @Test
    void getAttachmentStopsWhenAdministratorAccessIsDenied() {
        CustomException failure =
                new CustomException(ErrorCode.ADMIN_ACCESS_DENIED);
        doThrow(failure)
                .when(administratorService)
                .requireAccess(ADMIN_ID);

        assertThatThrownBy(() ->
                reportService.getAttachment(ADMIN_ID, REPORT_ID)
        ).isSameAs(failure);

        verifyNoInteractions(messageReportRepository, fileStorage);
    }

    @Test
    void getAttachmentRejectsMissingReport() {
        when(messageReportRepository.findDetailById(REPORT_ID))
                .thenReturn(Optional.empty());

        assertErrorCode(
                () -> reportService.getAttachment(ADMIN_ID, REPORT_ID),
                ErrorCode.REPORT_NOT_FOUND
        );

        verifyNoInteractions(fileStorage);
    }

    @Test
    void getAttachmentRejectsMessageWithoutAttachmentType() {
        MessageReport report = org.mockito.Mockito.mock(MessageReport.class);
        when(messageReportRepository.findDetailById(REPORT_ID))
                .thenReturn(Optional.of(report));
        when(report.getMessageTypeSnapshot()).thenReturn(ChatMessageType.TEXT);

        assertErrorCode(
                () -> reportService.getAttachment(ADMIN_ID, REPORT_ID),
                ErrorCode.ATTACHMENT_TYPE_NOT_ALLOWED
        );

        verifyNoInteractions(fileStorage);
    }

    @Test
    void getAttachmentRejectsMissingAttachment() {
        MessageReport report = org.mockito.Mockito.mock(MessageReport.class);
        ChatMessage message = org.mockito.Mockito.mock(ChatMessage.class);
        when(messageReportRepository.findDetailById(REPORT_ID))
                .thenReturn(Optional.of(report));
        when(report.getMessageTypeSnapshot()).thenReturn(ChatMessageType.IMAGE);
        when(report.getMessage()).thenReturn(message);
        when(message.getAttachment()).thenReturn(null);

        assertErrorCode(
                () -> reportService.getAttachment(ADMIN_ID, REPORT_ID),
                ErrorCode.ATTACHMENT_NOT_FOUND
        );

        verifyNoInteractions(fileStorage);
    }

    @ParameterizedTest
    @EnumSource(
            value = AttachmentStatus.class,
            names = {"PENDING", "DELETED"}
    )
    void getAttachmentRejectsInactiveAttachment(
            AttachmentStatus status
    ) {
        MessageReport report = org.mockito.Mockito.mock(MessageReport.class);
        ChatMessage message = org.mockito.Mockito.mock(ChatMessage.class);
        Attachment attachment = org.mockito.Mockito.mock(Attachment.class);
        when(messageReportRepository.findDetailById(REPORT_ID))
                .thenReturn(Optional.of(report));
        when(report.getMessageTypeSnapshot()).thenReturn(ChatMessageType.IMAGE);
        when(report.getMessage()).thenReturn(message);
        when(message.getAttachment()).thenReturn(attachment);
        when(attachment.getStatus()).thenReturn(status);

        assertErrorCode(
                () -> reportService.getAttachment(ADMIN_ID, REPORT_ID),
                ErrorCode.ATTACHMENT_NOT_FOUND
        );

        verifyNoInteractions(fileStorage);
    }

    @Test
    void getAttachmentReturnsActiveImage() {
        assertActiveAttachmentReturned(
                ChatMessageType.IMAGE,
                "image.png",
                "image/png"
        );
    }

    @Test
    void getAttachmentReturnsActiveFile() {
        assertActiveAttachmentReturned(
                ChatMessageType.FILE,
                "document.pdf",
                "application/pdf"
        );
    }

    @Test
    void deleteReportDeletesExistingReport() {
        MessageReport report = org.mockito.Mockito.mock(MessageReport.class);
        when(administratorService.hasAccess(ADMIN_ID)).thenReturn(true);
        when(messageReportRepository.findById(REPORT_ID))
                .thenReturn(Optional.of(report));

        reportService.deleteReport(ADMIN_ID, REPORT_ID);

        verify(messageReportRepository).delete(report);
    }

    @Test
    void deleteReportRejectsNonAdministrator() {
        when(administratorService.hasAccess(ADMIN_ID)).thenReturn(false);

        assertErrorCode(
                () -> reportService.deleteReport(ADMIN_ID, REPORT_ID),
                ErrorCode.ADMIN_ACCESS_DENIED
        );

        verifyNoInteractions(messageReportRepository, refreshTokenStore);
    }

    @Test
    void deleteReportRejectsMissingReport() {
        when(administratorService.hasAccess(ADMIN_ID)).thenReturn(true);
        when(messageReportRepository.findById(REPORT_ID))
                .thenReturn(Optional.empty());

        assertErrorCode(
                () -> reportService.deleteReport(ADMIN_ID, REPORT_ID),
                ErrorCode.REPORT_NOT_FOUND
        );

        verify(messageReportRepository, never())
                .delete(any(MessageReport.class));
    }

    @Test
    void memberSuspendSuspendsReportedMemberAndDeletesSessions() {
        Member reportedMember = org.mockito.Mockito.mock(Member.class);
        MessageReport report = org.mockito.Mockito.mock(MessageReport.class);
        when(administratorService.hasAccess(ADMIN_ID)).thenReturn(true);
        when(messageReportRepository.findById(REPORT_ID))
                .thenReturn(Optional.of(report));
        when(report.getReportedMember()).thenReturn(reportedMember);
        when(reportedMember.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(reportedMember.getId()).thenReturn(REPORTED_MEMBER_ID);

        Long result = reportService.memberSuspend(ADMIN_ID, REPORT_ID);

        assertThat(result).isEqualTo(REPORTED_MEMBER_ID);
        verify(reportedMember).suspendMember();
        verify(refreshTokenStore)
                .deleteAllByMemberId(REPORTED_MEMBER_ID);
    }

    @Test
    void memberSuspendRejectsNonAdministrator() {
        when(administratorService.hasAccess(ADMIN_ID)).thenReturn(false);

        assertErrorCode(
                () -> reportService.memberSuspend(ADMIN_ID, REPORT_ID),
                ErrorCode.ADMIN_ACCESS_DENIED
        );

        verifyNoInteractions(messageReportRepository, refreshTokenStore);
    }

    @Test
    void memberSuspendRejectsMissingReport() {
        when(administratorService.hasAccess(ADMIN_ID)).thenReturn(true);
        when(messageReportRepository.findById(REPORT_ID))
                .thenReturn(Optional.empty());

        assertErrorCode(
                () -> reportService.memberSuspend(ADMIN_ID, REPORT_ID),
                ErrorCode.REPORT_NOT_FOUND
        );

        verifyNoInteractions(refreshTokenStore);
    }

    @Test
    void memberSuspendRejectsWithdrawnMember() {
        Member reportedMember = org.mockito.Mockito.mock(Member.class);
        MessageReport report = org.mockito.Mockito.mock(MessageReport.class);
        when(administratorService.hasAccess(ADMIN_ID)).thenReturn(true);
        when(messageReportRepository.findById(REPORT_ID))
                .thenReturn(Optional.of(report));
        when(report.getReportedMember()).thenReturn(reportedMember);
        when(reportedMember.getStatus()).thenReturn(MemberStatus.WITHDRAWN);

        assertErrorCode(
                () -> reportService.memberSuspend(ADMIN_ID, REPORT_ID),
                ErrorCode.MEMBER_WITHDRAWN
        );

        verify(reportedMember, never()).suspendMember();
        verifyNoInteractions(refreshTokenStore);
    }

    private ReportFixture reportableMessage(
            ChatMessageType type,
            String content,
            Instant createdAt
    ) {
        ChatRoomParticipationContext context = stubParticipation();
        Member sender = org.mockito.Mockito.mock(Member.class);
        ChatMessage message = org.mockito.Mockito.mock(ChatMessage.class);

        lenient().when(sender.getId()).thenReturn(REPORTED_MEMBER_ID);
        when(message.getSender()).thenReturn(sender);
        when(message.getType()).thenReturn(type);
        lenient().when(message.getContent()).thenReturn(content);
        lenient().when(message.getCreatedAt()).thenReturn(createdAt);
        when(chatMessageRepository.findVisibleByIdAndChatRoomId(
                MESSAGE_ID,
                ROOM_ID,
                REPORTER_ID
        )).thenReturn(Optional.of(message));

        return new ReportFixture(
                context.member(),
                sender,
                message
        );
    }

    private ChatRoomParticipationContext stubParticipation() {
        Member reporter = org.mockito.Mockito.mock(Member.class);
        ChatRoom chatRoom = org.mockito.Mockito.mock(ChatRoom.class);
        ChatRoomMember chatRoomMember =
                org.mockito.Mockito.mock(ChatRoomMember.class);

        when(reporter.getId()).thenReturn(REPORTER_ID);
        when(chatRoom.getId()).thenReturn(ROOM_ID);
        when(chatRoomMember.getMember()).thenReturn(reporter);
        when(chatRoomMember.getChatRoom()).thenReturn(chatRoom);
        lenient().when(chatRoomMember.getJoinedAt()).thenReturn(JOINED_AT);

        ChatRoomParticipationContext context =
                new ChatRoomParticipationContext(
                        chatRoom,
                        reporter,
                        chatRoomMember
                );
        when(participationService.requireConversationParticipation(
                ROOM_ID,
                REPORTER_ID,
                ParticipationLockMode.NONE
        )).thenReturn(context);

        return context;
    }

    private MessageReportRequest request() {
        return new MessageReportRequest(
                MessageReportReason.SPAM,
                "반복 광고"
        );
    }

    private void assertActiveAttachmentReturned(
            ChatMessageType messageType,
            String originalName,
            String contentType
    ) {
        MessageReport report = org.mockito.Mockito.mock(MessageReport.class);
        ChatMessage message = org.mockito.Mockito.mock(ChatMessage.class);
        Attachment attachment = org.mockito.Mockito.mock(Attachment.class);
        Resource resource = org.mockito.Mockito.mock(Resource.class);
        String storageKey = "chat-attachments/storage-key";

        when(messageReportRepository.findDetailById(REPORT_ID))
                .thenReturn(Optional.of(report));
        when(report.getMessageTypeSnapshot()).thenReturn(messageType);
        when(report.getMessage()).thenReturn(message);
        when(message.getAttachment()).thenReturn(attachment);
        when(attachment.getStatus()).thenReturn(AttachmentStatus.ACTIVE);
        when(attachment.getStorageKey()).thenReturn(storageKey);
        when(attachment.getOriginalName()).thenReturn(originalName);
        when(attachment.getContentType()).thenReturn(contentType);
        when(attachment.getSizeBytes()).thenReturn(123L);
        when(fileStorage.load(storageKey)).thenReturn(resource);

        AttachmentDownloadResult result =
                reportService.getAttachment(ADMIN_ID, REPORT_ID);

        assertThat(result.resource()).isSameAs(resource);
        assertThat(result.originalName()).isEqualTo(originalName);
        assertThat(result.contentType()).isEqualTo(contentType);
        assertThat(result.sizeBytes()).isEqualTo(123L);
        verify(administratorService).requireAccess(ADMIN_ID);
        verify(fileStorage).load(storageKey);
    }

    private void assertReportRejected(ErrorCode expectedErrorCode) {
        assertErrorCode(
                () -> reportService.report(
                        REPORTER_ID,
                        ROOM_ID,
                        MESSAGE_ID,
                        request()
                ),
                expectedErrorCode
        );
    }

    private void assertErrorCode(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
            ErrorCode expectedErrorCode
    ) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(expectedErrorCode)
                );
    }

    private DataIntegrityViolationException databaseFailure(
            String constraintName
    ) {
        ConstraintViolationException constraintFailure =
                new ConstraintViolationException(
                        "DB 제약조건 위반",
                        new SQLException(),
                        constraintName
                );

        return new DataIntegrityViolationException(
                "DB 저장 실패",
                constraintFailure
        );
    }

    private record ReportFixture(
            Member reporter,
            Member sender,
            ChatMessage message
    ) {
    }
}
