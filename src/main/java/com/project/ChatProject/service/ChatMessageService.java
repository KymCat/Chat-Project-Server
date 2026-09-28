package com.project.ChatProject.service;

import com.project.ChatProject.dto.event.ChatMessageEvent;
import com.project.ChatProject.dto.request.ChatMessageRequest;
import com.project.ChatProject.dto.response.ChatMessageResponse;
import com.project.ChatProject.entity.ChatMessage;
import com.project.ChatProject.entity.ChatRoom;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.repository.ChatMessageRepository;
import com.project.ChatProject.service.support.ChatRoomParticipationContext;
import com.project.ChatProject.service.support.ParticipationLockMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatRoomParticipationService participation;
    private final ChatMessageRepository chatMessageRepository;

    @Transactional
    public ChatMessageEvent save(
            Long memberId,
            ChatMessageRequest request
    )
    {
        ChatRoomParticipationContext context =
                participation.requireParticipation(
                        request.roomId(),
                        memberId,
                        ParticipationLockMode.NONE
                );

        Member sender = context.member();
        ChatRoom chatRoom = context.chatRoom();

        String content = request.content().strip();
        ChatMessage message = ChatMessage.createText(
                chatRoom,
                sender,
                content
        );
        chatMessageRepository.save(message);

        chatRoom.updateLastMessageAt(message.getCreatedAt());

        return  ChatMessageEvent
                .created(ChatMessageResponse.from(message));
    }
}
