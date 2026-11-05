package com.project.ChatProject.controller;

import com.project.ChatProject.config.websocket.WebSocketMemberPrincipal;
import com.project.ChatProject.dto.event.ChatMessageEvent;
import com.project.ChatProject.dto.request.ChatAttachmentMessageRequest;
import com.project.ChatProject.dto.request.ChatMessageRequest;
import com.project.ChatProject.dto.response.DirectChatResponse;
import com.project.ChatProject.dto.result.ChatMessageSaveResult;
import com.project.ChatProject.jwt.AccessTokenClaims;
import com.project.ChatProject.service.ChatMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RestController;

/**
 * WebSocket 데이터 처리를 수행할 Controller
 *
 */
@RestController
@RequiredArgsConstructor
public class ChatController {

    private final SimpMessagingTemplate template;
    private final ChatMessageService chatMessageService;

    @MessageMapping("/msg")
    public ChatMessageEvent send(
            @Valid ChatMessageRequest request,
            Authentication authentication
    ) {
        WebSocketMemberPrincipal principal
                = resolvePrincipal(authentication);

        ChatMessageSaveResult result =
                chatMessageService.save(
                        principal.memberId(),
                        request
                );

        template.convertAndSend(
                "/sub/msg/" + request.roomId(),
                result.event()
        );

        notifyRejoinedDirectMember(result);

        return result.event();
    }

    @MessageMapping("/msg/attachment")
    public ChatMessageEvent sendAttachment(
            @Valid ChatAttachmentMessageRequest request,
            Authentication authentication
    ) {
        WebSocketMemberPrincipal principal
                = resolvePrincipal(authentication);

        ChatMessageSaveResult result =
                chatMessageService.saveAttachment(
                        principal.memberId(),
                        request
                );

        template.convertAndSend(
                "/sub/msg/" + request.roomId(),
                result.event()
        );

        notifyRejoinedDirectMember(result);

        return result.event();
    }

    private void notifyRejoinedDirectMember(
            ChatMessageSaveResult result
    ) {
        if (!result.hasRejoinedMember()) {
            return;
        }

        template.convertAndSendToUser(
                result.rejoinedMemberId().toString(),
                "/queue/direct-messages",
                DirectChatResponse.of(
                        result.event().message().roomId(),
                        result.event().message()
                )
        );
    }

    private WebSocketMemberPrincipal resolvePrincipal(
            Authentication authentication
    ) {
        return (WebSocketMemberPrincipal)
                authentication.getPrincipal();
    }
}
