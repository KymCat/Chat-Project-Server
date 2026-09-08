package com.project.ChatProject.controller;

import com.project.ChatProject.dto.ChatMessageDto;
import com.project.ChatProject.jwt.AccessTokenClaims;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * WebSocket 데이터 처리를 수행할 Controller
 *
 */
@RestController
@RequiredArgsConstructor
public class ChatController {

    private static final String TEMPORARY_NICKNAME_PREFIX = "member-";

    private final SimpMessagingTemplate template;

    @MessageMapping("/enter")
    public ChatMessageDto enter(
            ChatMessageDto chatMessageDto,
            Principal principal
    )
    {
        String sender = resolveSender(principal);

        chatMessageDto.setSender(sender);
        chatMessageDto.setContent(
                sender + "님이 입장하였습니다."
        );

        template.convertAndSend(
                "/sub/enter/" + chatMessageDto.getRoomId(),
                chatMessageDto
        );

        return chatMessageDto;
    }

    @MessageMapping("/msg")
    public ChatMessageDto send(
            ChatMessageDto chatMessageDto,
            Principal principal
    )
    {

        String sender = resolveSender(principal);

        chatMessageDto.setSender(sender);

        // convertAndSend() : STOMP 브로커를 통해 메세지를 발행(pub)할 때 쓰는 메서드
        template.convertAndSend(
                "/sub/msg/" + chatMessageDto.getRoomId(),
                chatMessageDto
        );

        return chatMessageDto;
    }

    private String resolveSender(Principal principal) {
        Authentication authentication =
                (Authentication) principal;

        AccessTokenClaims claims =
                (AccessTokenClaims) authentication.getPrincipal();

        return TEMPORARY_NICKNAME_PREFIX
                + claims.memberId();
    }
}
