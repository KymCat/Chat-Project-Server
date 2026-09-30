package com.project.ChatProject.service;

import com.project.ChatProject.dto.event.ChatMessageEvent;
import com.project.ChatProject.dto.request.ChatAttachmentMessageRequest;
import com.project.ChatProject.dto.request.ChatMessageRequest;
import com.project.ChatProject.dto.response.ChatMessageResponse;
import com.project.ChatProject.entity.Attachment;
import com.project.ChatProject.entity.ChatMessage;
import com.project.ChatProject.entity.ChatRoom;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.enums.AttachmentStatus;
import com.project.ChatProject.entity.enums.ChatMessageType;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.repository.AttachmentRepository;
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
    private final AttachmentRepository attachmentRepository;

    @Transactional
    public ChatMessageEvent save(
            Long memberId,
            ChatMessageRequest request
    ) {
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

    @Transactional
    public ChatMessageEvent saveAttachment(
            Long memberId,
            ChatAttachmentMessageRequest request
    ) {
        ChatRoomParticipationContext context =
                participation.requireParticipation(
                        request.roomId(),
                        memberId,
                        ParticipationLockMode.NONE
                );

        Attachment attachment = attachmentRepository
                .findByForUpdate(request.attachmentId())
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.ATTACHMENT_NOT_FOUND
                        )
                );

        validateAttachmentOwner(attachment, memberId);
        validateAttachmentRoom(attachment, context.chatRoom().getId());
        validatePendingAttachment(attachment);

        ChatMessageType messageType =
                resolveMessageType(attachment);

        ChatMessage message = ChatMessage.createAttachment(
                context.chatRoom(),
                context.member(),
                messageType,
                attachment
        );

        attachment.activate();
        chatMessageRepository.save(message);

        context.chatRoom()
                .updateLastMessageAt(message.getCreatedAt());

        return ChatMessageEvent
                .created(ChatMessageResponse.from(message));
    }

    // === private method ==

    /**
     * 첨부파일 업로더와 현재 로그인 유저가 같은지 검증
     * @param attachment
     * @param memberId
     */
    private void validateAttachmentOwner(
            Attachment attachment,
            Long memberId
    ) {
        if (!attachment.getUploader().getId().equals(memberId)) {
            throw new CustomException(
                    ErrorCode.ATTACHMENT_NOT_FOUND
            );
        }
    }

    /**
     * 첨부파일이 올라온 채팅방 ID가 같은지 검증
     * @param attachment
     * @param roomId
     */
    private void validateAttachmentRoom(
            Attachment attachment,
            Long roomId
    ) {
        if (!attachment.getChatRoom().getId().equals(roomId)) {
            throw new CustomException(
                    ErrorCode.ATTACHMENT_NOT_FOUND
            );
        }
    }

    /**
     * 보류중인 첨부파일인지 확인
     * @param attachment
     */
    private void validatePendingAttachment(Attachment attachment) {
        if (attachment.getStatus() != AttachmentStatus.PENDING) {
            throw new CustomException(
                    ErrorCode.ATTACHMENT_ALREADY_USED
            );
        }
    }

    /**
     * 첨부파일의 타입을 확인하여 반환
     * @param attachment
     * @return 이미지 or 파일 Enum
     */
    private ChatMessageType resolveMessageType(Attachment attachment) {
        if (attachment.getContentType().startsWith("image/")) {
            return ChatMessageType.IMAGE;
        }

        return ChatMessageType.FILE;
    }
}
