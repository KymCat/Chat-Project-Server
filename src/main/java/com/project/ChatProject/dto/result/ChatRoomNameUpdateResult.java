package com.project.ChatProject.dto.result;

import com.project.ChatProject.dto.event.ChatMessageEvent;
import com.project.ChatProject.dto.event.ChatRoomEvent;

public record ChatRoomNameUpdateResult(
        ChatRoomEvent chatRoomEvent,
        ChatMessageEvent chatMessageEvent
) {
}
