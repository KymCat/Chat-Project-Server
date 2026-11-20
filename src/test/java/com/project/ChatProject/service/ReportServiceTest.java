package com.project.ChatProject.service;

import com.project.ChatProject.dto.request.MessageReportRequest;
import com.project.ChatProject.entity.ChatMessage;
import com.project.ChatProject.entity.ChatRoom;
import com.project.ChatProject.entity.ChatRoomMember;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.MessageReport;
import com.project.ChatProject.entity.enums.ChatMessageType;
import com.project.ChatProject.entity.enums.MessageReportReason;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.repository.ChatMessageRepository;
import com.project.ChatProject.repository.MessageReportRepository;
import com.project.ChatProject.service.support.ChatRoomParticipationContext;
import com.project.ChatProject.service.support.ParticipationLockMode;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final Long REPORTER_ID = 1L;
    private static final Long REPORTED_MEMBER_ID = 2L;
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
