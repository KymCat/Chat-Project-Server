package com.project.ChatProject.dto;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;


/**
 * 구독자와 수신자 간의 메세지를 주고받는 형태를 구성한 Object
 *
 */
@Data
@Slf4j
public class ChatMessageDto {
    private String content;
    private String sender;
    private String type;
    private String roomId;

    public ChatMessageDto(String content, String sender, String type, String roomId) {
        this.content = content;
        this.sender = sender;
        this.type = type;
        this.roomId = roomId;
    }

}
