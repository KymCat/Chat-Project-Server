package com.project.ChatProject.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ChatEnterRequest(
        @NotNull
        @Positive
        Long roomId
) {
}
