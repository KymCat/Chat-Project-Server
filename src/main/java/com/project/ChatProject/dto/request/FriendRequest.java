package com.project.ChatProject.dto.request;

import jakarta.validation.constraints.NotNull;

public record FriendRequest(

        @NotNull(message = "친구 요청을 보낼 대상이 필요합니다.")
        Long targetMemberId
) {
}
