package com.project.ChatProject.dto.request;

import jakarta.validation.constraints.NotNull;

public record ChatRoomOwnerTransferRequest(
        @NotNull Long newOwnerMemberId
) {
}
