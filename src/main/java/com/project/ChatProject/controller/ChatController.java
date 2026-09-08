package com.project.ChatProject.controller;

import com.project.ChatProject.config.websocket.WebSocketMemberPrincipal;
import com.project.ChatProject.dto.request.ChatMessageRequest;
import com.project.ChatProject.dto.response.ChatMessageResponse;
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

    @MessageMapping("/enter")
    public ChatMessageResponse enter(
            ChatMessageRequest request,
            Authentication authentication
    )
    {
        WebSocketMemberPrincipal principal =
                resolvePrincipal(authentication);

        ChatMessageResponse response =
                new ChatMessageResponse(
                        principal.nickname() + "님이 입장하였습니다.",
                        principal.memberId(),
                        principal.nickname(),
                        ENTER_TYPE,
                        request.roomId()
                );

        template.convertAndSend(
                "/sub/enter/" + request.roomId(),
                response
        );

        return response;
    }

    @MessageMapping("/msg")
    public ChatMessageResponse send(
            ChatMessageRequest request,
            Authentication authentication
    )
    {
        WebSocketMemberPrincipal principal
                = resolvePrincipal(authentication);

        ChatMessageResponse response =
                new ChatMessageResponse(
                        request.content(),
                        principal.memberId(),
                        principal.nickname(),
                        CHAT_TYPE,
                        request.roomId()
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
