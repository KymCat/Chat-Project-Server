package com.project.ChatProject.dto.event;

import com.project.ChatProject.exception.ErrorCode;

public record ChatErrorEvent(
        Long roomId,
        String code,
        String message
) {
    public static ChatErrorEvent from(
            Long roomId,
            ErrorCode errorCode
    ) {
        return new ChatErrorEvent(
                roomId,
                errorCode.getCode(),
                errorCode.getMessage()
        );
    }
}
