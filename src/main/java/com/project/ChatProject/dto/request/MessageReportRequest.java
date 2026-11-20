package com.project.ChatProject.dto.request;

import com.project.ChatProject.entity.enums.MessageReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MessageReportRequest(

        @NotNull(message = "신고 사유를 골라주세요.")
        MessageReportReason reason,

        @Size(
                max = 500,
                message = "신고 상세 내용은 500자 이하여야 합니다."
        )
        String detail
) {
}
