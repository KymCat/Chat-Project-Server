package com.project.ChatProject.dto.request;

import com.project.ChatProject.invite.InviteLinkExpiration;
import jakarta.validation.constraints.NotNull;

public record ChatRoomInviteLinkRequest(

        @NotNull(message = "초대 링크 만료 시간을 선택해야 합니다.")
        InviteLinkExpiration expiration
) {
}
