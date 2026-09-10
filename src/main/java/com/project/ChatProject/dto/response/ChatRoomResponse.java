package com.project.ChatProject.dto.response;

import com.project.ChatProject.entity.ChatRoom;
import com.project.ChatProject.entity.ChatRoomMember;
import com.project.ChatProject.entity.enums.ChatRoomMemberRole;
import com.project.ChatProject.entity.enums.ChatRoomType;

import java.time.Instant;

public record ChatRoomResponse(
        Long roomId,
        ChatRoomType type,
        String name,
        ChatRoomMemberRole role,
        Instant lastMessageAt
) {
    public static ChatRoomResponse of(ChatRoomMember chatRoomMember) {
        ChatRoom chatRoom = chatRoomMember.getChatRoom();

        return new ChatRoomResponse(
                chatRoom.getId(),
                chatRoom.getType(),
                chatRoom.getName(),
                chatRoomMember.getRole(),
                chatRoom.getLastMessageAt()
        );

    }
}
