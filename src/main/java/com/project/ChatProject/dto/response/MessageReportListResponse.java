package com.project.ChatProject.dto.response;

import com.project.ChatProject.entity.MessageReport;
import com.project.ChatProject.entity.enums.MemberStatus;
import com.project.ChatProject.entity.enums.MessageReportReason;

import java.time.Instant;

public record MessageReportListResponse(
        Long reportId,
        MessageReportReason reason,
        Long reporterId,
        String reporterNickname,
        Long reportedMemberId,
        String reportedMemberNickname,
        MemberStatus reportedMemberStatus,
        Long messageId,
        Instant createdAt
) {

    public static MessageReportListResponse from(MessageReport report) {
        return new MessageReportListResponse(
                report.getId(),
                report.getReason(),
                report.getReporter().getId(),
                report.getReporter().getNickname(),
                report.getReportedMember().getId(),
                report.getReportedMember().getNickname(),
                report.getReportedMember().getStatus(),
                report.getMessage().getId(),
                report.getCreatedAt()
        );
    }
}
