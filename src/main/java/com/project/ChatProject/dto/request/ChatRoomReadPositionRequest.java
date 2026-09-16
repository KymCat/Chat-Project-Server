package com.project.ChatProject.dto.request;

import jakarta.validation.constraints.NotNull;

public record ChatRoomReadPositionRequest(
        @NotNull Long lastReadMessageId
) {
}
