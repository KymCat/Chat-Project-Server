package com.project.ChatProject.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ChatAttachmentMessageRequest(
        @NotNull(message = "채팅방 ID는 필수입니다.")
        @Positive(message = "채팅방 ID는 양수이어야 합니다.")
        Long roomId,

        @NotNull(message = "첨부파일 ID는 필수입니다.")
        @Positive(message = "첨부파일 ID는 양수이어야 합니다.")
        Long attachmentId
) {
}
