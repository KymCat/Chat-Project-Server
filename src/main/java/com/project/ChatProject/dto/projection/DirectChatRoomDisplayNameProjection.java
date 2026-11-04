package com.project.ChatProject.dto.projection;

import com.project.ChatProject.entity.enums.MemberStatus;

public interface DirectChatRoomDisplayNameProjection {

    Long getRoomId();

    String getNickname();

    MemberStatus getStatus();
}
