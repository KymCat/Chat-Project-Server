package com.project.ChatProject.service;

import com.project.ChatProject.dto.request.MessageReportRequest;
import com.project.ChatProject.dto.response.MessageReportDetailResponse;
import com.project.ChatProject.dto.response.MessageReportListResponse;
import com.project.ChatProject.entity.*;
import com.project.ChatProject.entity.enums.ChatMessageType;
import com.project.ChatProject.entity.enums.MemberStatus;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.jwt.refresh.RefreshTokenStore;
import com.project.ChatProject.repository.ChatMessageRepository;
import com.project.ChatProject.repository.MessageReportRepository;
import com.project.ChatProject.service.support.ChatRoomParticipationContext;
import com.project.ChatProject.service.support.ParticipationLockMode;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final int PAGE_SIZE = 20;

    private final AdministratorService administratorService;
    private final MessageReportRepository messageReportRepository;
    private final ChatRoomParticipationService participationService;
    private final ChatMessageRepository chatMessageRepository;
    private final RefreshTokenStore refreshTokenStore;

    @Transactional(readOnly = true)
    public PagedModel<MessageReportListResponse> getMessageReports(
            Long memberId,
            int page
    ) {

        administratorService.requireAccess(memberId);

        Page<MessageReport> messageReports = messageReportRepository
                .findAllMessageReport(
                        PageRequest.of(page, PAGE_SIZE)
                );

        return new PagedModel<>(
                messageReports
                        .map(MessageReportListResponse::from)
        );
    }

    @Transactional(readOnly = true)
    public MessageReportDetailResponse getMessageReport(
            Long memberId,
            Long reportId

    ) {
        administratorService.requireAccess(memberId);

        MessageReport report = messageReportRepository
                .findDetailById(reportId)
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.REPORT_NOT_FOUND
                        )
                );

        return MessageReportDetailResponse.of(report);
    }

    @Transactional
    public void report(
            Long reporterId,
            Long roomId,
            Long messageId,
            MessageReportRequest request
    ) {
        // 신고자 검증
        ChatRoomParticipationContext reporterContext =
                participationService.requireConversationParticipation(
                        roomId,
                        reporterId,
                        ParticipationLockMode.NONE
                );

        // 채팅 메세지 검증
        ChatMessage chatMessage = validateChatMessage(
                messageId,
                reporterContext.chatRoomMember()
        );

        if (messageReportRepository.existsByReporterIdAndMessageId(
                reporterId,
                messageId
        )) {
            throw new CustomException(
                    ErrorCode.REPORT_ALREADY_EXISTS
            );
        }

        MessageReport messageReport = MessageReport.create(
                reporterContext.member(),
                chatMessage.getSender(),
                chatMessage,
                request.reason(),
                request.detail()
        );

        try {
            messageReportRepository.saveAndFlush(messageReport);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateReport(exception)) {
                throw new CustomException(
                        ErrorCode.REPORT_ALREADY_EXISTS
                );
            }

            throw exception;
        }
    }

    @Transactional
    public void deleteReport(
            Long memberId,
            Long reportId
    ) {
        if (!administratorService.hasAccess(memberId)) {
            throw new CustomException(
                    ErrorCode.ADMIN_ACCESS_DENIED
            );
        }

        MessageReport report = messageReportRepository
                .findById(reportId)
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.REPORT_NOT_FOUND
                        )
                );

        messageReportRepository.delete(report);
    }

    @Transactional
    public Long memberSuspend(
            Long memberId,
            Long reportId
    ) {
        if (!administratorService.hasAccess(memberId)) {
            throw new CustomException(
                    ErrorCode.ADMIN_ACCESS_DENIED
            );
        }

        MessageReport report = messageReportRepository
                .findById(reportId)
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.REPORT_NOT_FOUND
                        )
                );

        Member reportedMember = report.getReportedMember();
        if (reportedMember.getStatus().equals(MemberStatus.WITHDRAWN)) {
            throw new CustomException(
                    ErrorCode.MEMBER_WITHDRAWN
            );
        }

        reportedMember.suspendMember();

        refreshTokenStore
                .deleteAllByMemberId(reportedMember.getId());

        return reportedMember.getId();
    }

    // === Private Method ===

    private boolean isDuplicateReport(
            DataIntegrityViolationException exception
    ) {
        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraint) {
                return "uq_message_reports_reporter_id_message_id"
                        .equals(constraint.getConstraintName());
            }

            cause = cause.getCause();
        }

        return false;
    }

    private ChatMessage validateChatMessage(
            Long messageId,
            ChatRoomMember chatRoomMember
    ) {
        Long roomId = chatRoomMember.getChatRoom().getId();
        Long reporterId = chatRoomMember.getMember().getId();

        ChatMessage chatMessage = chatMessageRepository
                .findVisibleByIdAndChatRoomId(
                        messageId,
                        roomId,
                        reporterId
                )
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.CHAT_MESSAGE_NOT_FOUND
                        )
                );

        if (chatMessage.getSender() == null
                || chatMessage.getType() == ChatMessageType.SYSTEM) {
            throw new CustomException(
                    ErrorCode.REPORT_DENIED
            );
        }

        if (chatMessage.getDeletedAt() != null) {
            throw new CustomException(
                    ErrorCode.REPORT_DENIED
            );
        }

        if (chatMessage.getSender().getId().equals(reporterId)) {
            throw new CustomException(
                    ErrorCode.REPORT_MEMBER_ID_SAME
            );
        }

        if (chatMessage.getCreatedAt()
                .isBefore(chatRoomMember.getJoinedAt())
        ) {
            throw new CustomException(
                    ErrorCode.CHAT_MESSAGE_NOT_FOUND
            );
        }

        return chatMessage;
    }
}
