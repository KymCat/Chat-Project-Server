package com.project.ChatProject.storage;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum StorageCategory {
    CHAT_ATTACHMENT("attachments"),
    MEMBER_PROFILE("member-profiles"),
    CHAT_ROOM_PROFILE("chat-room-profiles");

    private final String prefix;

    public String getPrefix() {
        return prefix;
    }
}
