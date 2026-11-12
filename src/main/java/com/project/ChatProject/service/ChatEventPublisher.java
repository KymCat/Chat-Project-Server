package com.project.ChatProject.service;

import com.project.ChatProject.dto.event.ChatMessageEvent;
import com.project.ChatProject.dto.event.ChatRoomEvent;
import com.project.ChatProject.repository.ChatRoomMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ChatEventPublisher {

    private static final String CHAT_MESSAGE_DESTINATION =
            "/queue/chat-messages";
    private static final String CHAT_ROOM_EVENT_DESTINATION =
            "/queue/chat-room-events";

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatRoomMemberRepository chatRoomMemberRepository;

    public void publishMessage(
            ChatMessageEvent event,
            Long visibilityMemberId
    ) {

        Long roomId = event.message().roomId();

        List<Long> recipientIds = chatRoomMemberRepository
                .findVisibleRecipientIds(
                        roomId,
                        visibilityMemberId
                );

        recipientIds.forEach(recipientId ->
                messagingTemplate.convertAndSendToUser(
                        recipientId.toString(),
                        CHAT_MESSAGE_DESTINATION,
                        event
                )
        );
    }

    public void publishChatRoomEvent(ChatRoomEvent event) {

        Long roomId = event.roomId();

        List<Long> recipientIds = chatRoomMemberRepository
                .findRecipientIds(roomId);

        recipientIds.forEach(recipientId ->
                messagingTemplate.convertAndSendToUser(
                        recipientId.toString(),
                        CHAT_ROOM_EVENT_DESTINATION,
                        event
                )
        );
    }
}
