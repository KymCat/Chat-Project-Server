package com.project.ChatProject.dto.request;

import jakarta.validation.constraints.NotNull;

public record MuteRequest(

        @NotNull(message = "알림 설정은 필수입니다.")
        Boolean muted

) {
}
