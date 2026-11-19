package com.project.ChatProject.dto.response;

import com.project.ChatProject.entity.ChatMessage;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.MessageReport;
import com.project.ChatProject.entity.enums.ChatMessageType;
import com.project.ChatProject.entity.enums.MemberStatus;
import com.project.ChatProject.entity.enums.MessageReportReason;

import java.time.Instant;

public record MessageReportDetailResponse(
        Long reportId,
        MessageReportReason reason,
        String details,
        Instant reportedAt,

        Long reporterId,
        String reporterNickname,

        Long reportedMemberId,
        String reportedMemberEmail,
        String reportedMemberNickname,
        MemberStatus reportedMemberStatus,

        Long roomId,
        Long messageId,
        ChatMessageType messageType,
        String messageContent,
        AttachmentResponse attachment,
        Instant messageCreatedAt,
        Instant messageEditedAt,
        boolean messageDeleted
) {

    public static MessageReportDetailResponse of(MessageReport report) {
        Member reporter = report.getReporter();
        Member reportedMember = report.getReportedMember();
        ChatMessage message = report.getMessage();

        AttachmentResponse attachment =
                message.getAttachment() != null
                        ? AttachmentResponse.from(message.getAttachment())
                        : null;

        return new MessageReportDetailResponse(
                report.getId(),
                report.getReason(),
                report.getDetail(),
                report.getCreatedAt(),

                reporter.getId(),
                reporter.getNickname(),

                reportedMember.getId(),
                reportedMember.getEmail(),
                reportedMember.getNickname(),
                reportedMember.getStatus(),

                message.getChatRoom().getId(),
                message.getId(),
                report.getMessageTypeSnapshot(),
                report.getMessageContentSnapshot(),
                attachment,
                message.getCreatedAt(),
                message.getEditedAt(),
                message.getDeletedAt() != null
        );
    }
}