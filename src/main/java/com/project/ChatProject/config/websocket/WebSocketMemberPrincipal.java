package com.project.ChatProject.config.websocket;

import java.security.Principal;

public record WebSocketMemberPrincipal(
        Long memberId,
        String nickname
) implements Principal {

    @Override
    public String getName() {
        return memberId.toString();
    }
}
