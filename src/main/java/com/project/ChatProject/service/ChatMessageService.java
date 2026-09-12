package com.project.ChatProject.service;

import com.project.ChatProject.dto.request.ChatMessageRequest;
import com.project.ChatProject.dto.response.ChatMessageResponse;
import com.project.ChatProject.entity.ChatMessage;
import com.project.ChatProject.entity.ChatRoom;
import com.project.ChatProject.entity.ChatRoomMember;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.enums.MemberStatus;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.repository.ChatMessageRepository;
import com.project.ChatProject.repository.ChatRoomMemberRepository;
import com.project.ChatProject.repository.ChatRoomRepository;
import com.project.ChatProject.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final MemberRepository memberRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatMessageRepository chatMessageRepository;

    public ChatMessageResponse enter(
            Long memberId,
            String nickname,
            Long roomId,
            String chatType
    )
    {
        String content = nickname + "님이 입장하였습니다.";
        Member sender = findMember(memberId);
        validateMember(sender);

        ChatRoom chatRoom = chatRoomRepository
                .findById(roomId)
                .orElseThrow(()->
                        new CustomException(
                                ErrorCode.CHAT_ROOM_NOT_FOUND
                        )
                );

        if (chatRoom.getDeletedAt() != null) {
            throw new CustomException(ErrorCode.CHAT_ROOM_DELETED);
        }
        validateParticipation(roomId, memberId);

        return new ChatMessageResponse(
                content,
                memberId,
                nickname,
                chatType,
                roomId
        );

    }

    @Transactional
    public ChatMessageResponse save(
            Long memberId,
            ChatMessageRequest request,
            String chatType
    )
    {
        Long roomId = request.roomId();
        Member sender = findMember(memberId);
        validateMember(sender);

        ChatRoom chatRoom = chatRoomRepository
                .findById(roomId)
                .orElseThrow(()->
                        new CustomException(
                                ErrorCode.CHAT_ROOM_NOT_FOUND
                        )
                );

        if (chatRoom.getDeletedAt() != null) {
            throw new CustomException(ErrorCode.CHAT_ROOM_DELETED);
        }
        validateParticipation(roomId, memberId);

        String content = request.content().strip();
        ChatMessage message = ChatMessage.createText(
                chatRoom,
                sender,
                content
        );
        chatMessageRepository.save(message);

        chatRoom.updateLastMessageAt(message.getCreatedAt());
        return ChatMessageResponse.of(
                message,
                sender,
                chatType
        );
    }

    // == Private Method ==

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.MEMBER_NOT_FOUND
                        )
                );
    }

    private void validateMember(Member member) {
        if (member.getStatus() == MemberStatus.SUSPENDED) {
            throw new CustomException(ErrorCode.MEMBER_BLOCKED);
        }

        if (member.getStatus() == MemberStatus.WITHDRAWN) {
            throw new CustomException(ErrorCode.MEMBER_WITHDRAWN);
        }

        if (member.getEmailVerifiedAt() == null) {
            throw new CustomException(ErrorCode.EMAIL_VERIFICATION_REQUIRED);
        }
    }

    private void validateParticipation(
            Long roomId,
            Long memberId
    )
    {
        ChatRoomMember chatRoomMember =
                chatRoomMemberRepository
                        .findByChatRoomIdAndMemberId(roomId, memberId)
                        .orElseThrow(()->
                                new CustomException(ErrorCode.CHAT_ROOM_ACCESS_DENIED)
                        );
        if (!chatRoomMember.isParticipating())
            throw new CustomException(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
    }
}
