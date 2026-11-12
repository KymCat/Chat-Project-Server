package com.project.ChatProject.controller;

import com.project.ChatProject.config.websocket.WebSocketMemberPrincipal;
import com.project.ChatProject.dto.event.ChatErrorEvent;
import com.project.ChatProject.dto.event.ChatMessageEvent;
import com.project.ChatProject.dto.request.ChatAttachmentMessageRequest;
import com.project.ChatProject.dto.request.ChatMessageRequest;
import com.project.ChatProject.dto.response.AttachmentResponse;
import com.project.ChatProject.dto.response.ChatMessageResponse;
import com.project.ChatProject.dto.response.DirectChatResponse;
import com.project.ChatProject.dto.result.ChatMessageSaveResult;
import com.project.ChatProject.dto.event.ChatMessageEventType;
import com.project.ChatProject.entity.enums.ChatMessageType;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.publisher.ChatEventPublisher;
import com.project.ChatProject.service.ChatMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private SimpMessagingTemplate template;

    @Mock
    private ChatMessageService chatMessageService;

    @Mock
    private ChatEventPublisher publisher;

    private ChatController chatController;

    @BeforeEach
    void setUp() {
        chatController = new ChatController(
                template,
                chatMessageService,
                publisher
        );
    }

    @Test
    void sendCreatesAuthenticatedMemberResponseAndBroadcastsChatMessage() {
        Instant createdAt = Instant.parse("2026-09-12T06:00:00Z");
        ChatMessageRequest request = new ChatMessageRequest(
                10L,
                "안녕하세요"
        );
        Authentication authentication = authentication(
                1L,
                "홍길동"
        );
        ChatMessageResponse message =
                new ChatMessageResponse(
                        100L,
                        10L,
                        1L,
                        "홍길동",
                        ChatMessageType.TEXT,
                        "안녕하세요",
                        null,
                        createdAt,
                        null,
                        false
                );
        ChatMessageEvent expectedEvent = ChatMessageEvent.created(message);
        when(chatMessageService.save(
                1L,
                request
        )).thenReturn(ChatMessageSaveResult.of(expectedEvent, null));

        ChatMessageEvent result = chatController.send(
                request,
                authentication
        );

        assertThat(result).isEqualTo(expectedEvent);
        assertThat(result.eventType()).isEqualTo(ChatMessageEventType.CREATED);
        assertThat(result.message()).isEqualTo(message);
        verify(chatMessageService).save(
                1L,
                request
        );
        verify(publisher).publishMessage(result, 1L);
        verify(template, never()).convertAndSendToUser(
                any(String.class),
                any(String.class),
                any()
        );
    }

    @Test
    void sendAttachmentBroadcastsAttachmentMessage() {
        Instant createdAt = Instant.parse("2026-09-28T06:00:00Z");
        ChatAttachmentMessageRequest request =
                new ChatAttachmentMessageRequest(10L, 200L);
        Authentication authentication = authentication(1L, "홍길동");
        AttachmentResponse attachment = new AttachmentResponse(
                200L,
                "image.png",
                "image/png",
                1_024L
        );
        ChatMessageResponse message = new ChatMessageResponse(
                101L,
                10L,
                1L,
                "홍길동",
                ChatMessageType.IMAGE,
                null,
                attachment,
                createdAt,
                null,
                false
        );
        ChatMessageEvent expectedEvent = ChatMessageEvent.created(message);
        when(chatMessageService.saveAttachment(1L, request))
                .thenReturn(ChatMessageSaveResult.of(expectedEvent, 2L));

        ChatMessageEvent result = chatController.sendAttachment(
                request,
                authentication
        );

        assertThat(result).isEqualTo(expectedEvent);
        verify(chatMessageService).saveAttachment(1L, request);
        verify(publisher).publishMessage(expectedEvent, 1L);
        verify(template).convertAndSendToUser(
                "2",
                "/queue/direct-messages",
                DirectChatResponse.of(10L, message)
        );
    }

    @Test
    void sendNotifiesRequesterWhenDirectFriendshipNoLongerExists() {
        ChatMessageRequest request = new ChatMessageRequest(
                10L,
                "안녕하세요"
        );
        Authentication authentication = authentication(1L, "홍길동");
        when(chatMessageService.save(1L, request))
                .thenThrow(new CustomException(
                        ErrorCode.FRIEND_RELATION_NOT_FOUND
                ));

        ChatMessageEvent result = chatController.send(
                request,
                authentication
        );

        assertThat(result).isNull();
        verify(publisher, never()).publishMessage(
                any(ChatMessageEvent.class),
                any(Long.class)
        );
        verify(template).convertAndSendToUser(
                "1",
                "/queue/chat-errors",
                ChatErrorEvent.from(
                        10L,
                        ErrorCode.FRIEND_RELATION_NOT_FOUND
                )
        );
    }

    @Test
    void sendAttachmentNotifiesRequesterWhenDirectFriendshipNoLongerExists() {
        ChatAttachmentMessageRequest request =
                new ChatAttachmentMessageRequest(10L, 200L);
        Authentication authentication = authentication(1L, "홍길동");
        when(chatMessageService.saveAttachment(1L, request))
                .thenThrow(new CustomException(
                        ErrorCode.FRIEND_RELATION_NOT_FOUND
                ));

        ChatMessageEvent result = chatController.sendAttachment(
                request,
                authentication
        );

        assertThat(result).isNull();
        verify(publisher, never()).publishMessage(
                any(ChatMessageEvent.class),
                any(Long.class)
        );
        verify(template).convertAndSendToUser(
                "1",
                "/queue/chat-errors",
                ChatErrorEvent.from(
                        10L,
                        ErrorCode.FRIEND_RELATION_NOT_FOUND
                )
        );
    }

    @Test
    void sendNotifiesRequesterWhenDirectReceiverIsWithdrawn() {
        ChatMessageRequest request = new ChatMessageRequest(
                10L,
                "안녕하세요"
        );
        Authentication authentication = authentication(1L, "홍길동");
        when(chatMessageService.save(1L, request))
                .thenThrow(new CustomException(
                        ErrorCode.MEMBER_WITHDRAWN
                ));

        ChatMessageEvent result = chatController.send(
                request,
                authentication
        );

        assertThat(result).isNull();
        verify(publisher, never()).publishMessage(
                any(ChatMessageEvent.class),
                any(Long.class)
        );
        verify(template).convertAndSendToUser(
                "1",
                "/queue/chat-errors",
                ChatErrorEvent.from(
                        10L,
                        ErrorCode.MEMBER_WITHDRAWN
                )
        );
    }

    @Test
    void sendAttachmentNotifiesRequesterWhenDirectReceiverIsWithdrawn() {
        ChatAttachmentMessageRequest request =
                new ChatAttachmentMessageRequest(10L, 200L);
        Authentication authentication = authentication(1L, "홍길동");
        when(chatMessageService.saveAttachment(1L, request))
                .thenThrow(new CustomException(
                        ErrorCode.MEMBER_WITHDRAWN
                ));

        ChatMessageEvent result = chatController.sendAttachment(
                request,
                authentication
        );

        assertThat(result).isNull();
        verify(publisher, never()).publishMessage(
                any(ChatMessageEvent.class),
                any(Long.class)
        );
        verify(template).convertAndSendToUser(
                "1",
                "/queue/chat-errors",
                ChatErrorEvent.from(
                        10L,
                        ErrorCode.MEMBER_WITHDRAWN
                )
        );
    }

    private Authentication authentication(
            Long memberId,
            String nickname
    ) {
        WebSocketMemberPrincipal principal =
                new WebSocketMemberPrincipal(
                memberId,
                        nickname
                );

        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of()
        );
    }
}
