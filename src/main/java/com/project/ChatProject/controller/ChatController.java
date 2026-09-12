package com.project.ChatProject.controller;

import com.project.ChatProject.config.websocket.WebSocketMemberPrincipal;
import com.project.ChatProject.dto.request.ChatEnterRequest;
import com.project.ChatProject.dto.request.ChatMessageRequest;
import com.project.ChatProject.dto.response.ChatMessageResponse;
import com.project.ChatProject.service.ChatMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

/**
 * WebSocket 데이터 처리를 수행할 Controller
 *
 */
@RestController
@RequiredArgsConstructor
public class ChatController {

    private static final String ENTER_TYPE = "ENTER";
    private static final String CHAT_TYPE = "CHAT";

    private final SimpMessagingTemplate template;
    private final ChatMessageService chatMessageService;

    @MessageMapping("/enter")
    public ChatMessageResponse enter(
            @Valid ChatEnterRequest request,
            Authentication authentication
    )
    {
        WebSocketMemberPrincipal principal =
                resolvePrincipal(authentication);

        ChatMessageResponse response =
               chatMessageService.enter(
                       principal.memberId(),
                       principal.nickname(),
                       request.roomId(),
                       ENTER_TYPE
               );

        template.convertAndSend(
                "/sub/enter/" + request.roomId(),
                response
        );

        return response;
    }

    @MessageMapping("/msg")
    public ChatMessageResponse send(
            @Valid ChatMessageRequest request,
            Authentication authentication
    )
    {
        WebSocketMemberPrincipal principal
                = resolvePrincipal(authentication);

        ChatMessageResponse response =
                chatMessageService.save(
                        principal.memberId(),
                        request,
                        CHAT_TYPE
                );

        template.convertAndSend(
                "/sub/msg/" + request.roomId(),
                response
        );

        return response;
    }

    private WebSocketMemberPrincipal resolvePrincipal(
            Authentication authentication
    )
    {
        return (WebSocketMemberPrincipal)
                authentication.getPrincipal();
    }
}
