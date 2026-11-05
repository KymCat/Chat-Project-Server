package com.project.ChatProject.dto.result;

import com.project.ChatProject.dto.event.ChatMessageEvent;

public record ChatMessageSaveResult(
        ChatMessageEvent event,
        Long rejoinedMemberId
) {

    public static ChatMessageSaveResult of(
            ChatMessageEvent event,
            Long rejoinedMemberId
    ) {
        return new ChatMessageSaveResult(
                event,
                rejoinedMemberId
        );
    }

    public boolean hasRejoinedMember() {
        return rejoinedMemberId != null;
    }
}
