package com.project.ChatProject.service;

import com.project.ChatProject.dto.event.ChatMessageEvent;
import com.project.ChatProject.dto.event.ChatRoomEvent;
import com.project.ChatProject.dto.response.ChatMessageResponse;
import com.project.ChatProject.entity.enums.ChatMessageType;
import com.project.ChatProject.publisher.ChatEventPublisher;
import com.project.ChatProject.repository.ChatRoomMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatEventPublisherTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @InjectMocks
    private ChatEventPublisher publisher;

    @Test
    void publishMessageSendsEventOnlyToVisibleRecipients() {
        ChatMessageResponse message = new ChatMessageResponse(
                100L,
                10L,
                1L,
                "홍길동",
                ChatMessageType.TEXT,
                "안녕하세요",
                null,
                Instant.parse("2026-11-12T06:00:00Z"),
                null,
                false
        );
        ChatMessageEvent event = ChatMessageEvent.created(message);
        when(chatRoomMemberRepository.findVisibleRecipientIds(10L, 1L))
                .thenReturn(List.of(1L, 3L));

        publisher.publishMessage(event, 1L);

        verify(chatRoomMemberRepository)
                .findVisibleRecipientIds(10L, 1L);
        verify(messagingTemplate).convertAndSendToUser(
                "1",
                "/queue/chat-messages",
                event
        );
        verify(messagingTemplate).convertAndSendToUser(
                "3",
                "/queue/chat-messages",
                event
        );
        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    void publishChatRoomEventSendsEventToAllActiveRecipients() {
        ChatRoomEvent event = ChatRoomEvent.updated(10L, "변경된 채팅방");
        when(chatRoomMemberRepository.findRecipientIds(10L))
                .thenReturn(List.of(1L, 2L));

        publisher.publishChatRoomEvent(event);

        verify(chatRoomMemberRepository).findRecipientIds(10L);
        verify(messagingTemplate).convertAndSendToUser(
                "1",
                "/queue/chat-room-events",
                event
        );
        verify(messagingTemplate).convertAndSendToUser(
                "2",
                "/queue/chat-room-events",
                event
        );
        verifyNoMoreInteractions(messagingTemplate);
    }
}
