package com.project.ChatProject.service.support;

import com.project.ChatProject.entity.ChatRoom;
import com.project.ChatProject.entity.ChatRoomMember;
import com.project.ChatProject.entity.Member;

public record ChatRoomParticipationContext(
        ChatRoom chatRoom,
        Member member,
        ChatRoomMember chatRoomMember
) {
}
