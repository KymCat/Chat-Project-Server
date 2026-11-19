package com.project.ChatProject.dto.response;

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
}