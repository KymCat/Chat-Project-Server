package com.project.ChatProject.dto.websocket;

public record AuthWebSocketEvent(
        Type type
) {

    public static AuthWebSocketEvent forceLogout() {
        return new AuthWebSocketEvent(Type.FORCE_LOGOUT);
    }

    public enum Type {
        FORCE_LOGOUT
    }
}