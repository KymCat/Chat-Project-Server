package com.project.ChatProject.service;

import com.project.ChatProject.entity.ChatRoom;
import com.project.ChatProject.entity.ChatRoomMember;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.enums.ChatRoomType;
import com.project.ChatProject.entity.enums.MemberStatus;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.repository.ChatRoomMemberRepository;
import com.project.ChatProject.repository.ChatRoomRepository;
import com.project.ChatProject.repository.MemberRepository;
import com.project.ChatProject.service.support.ChatRoomParticipationContext;
import com.project.ChatProject.service.support.ParticipationLockMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatRoomParticipationService {

    private final MemberRepository memberRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatRoomRepository chatRoomRepository;

    /**
     * 멤버 검증 이후에 멤버 엔티티 반환
     * @param memberId
     * @return
     */
    public Member requireActiveMember(Long memberId) {
        Member member = findMember(memberId);
        validateMember(member);
        return member;
    }

    /**
     * 채팅방 검증 이후에 채팅방 엔티티 반환
     * @param roomId
     * @return
     */
    public ChatRoom requireJoinableChatRoom(Long roomId) {
        ChatRoom chatRoom = findChatRoom(roomId);
        validateJoinableChatRoom(chatRoom);
        return chatRoom;
    }

    /**
     * 채팅방과 채팅방멤버, 멤버 엔티티 검증 및 Lock
     * @param roomId
     * @param memberId
     * @param lockMode
     * @return ChatRoomParticipationContext
     */
    public ChatRoomParticipationContext requireParticipation(
            Long roomId,
            Long memberId,
            ParticipationLockMode lockMode
    ) {
        ChatRoom chatRoom = switch (lockMode) {
            case CHAT_ROOM -> findChatRoomLock(roomId);
            case CHAT_ROOM_MEMBER, NONE -> findChatRoom(roomId);
        };
        validateJoinableChatRoom(chatRoom);

        Member member = findMember(memberId);
        validateMember(member);

        ChatRoomMember chatRoomMember = switch (lockMode) {
            case CHAT_ROOM_MEMBER ->
                    findChatRoomMemberForUpdate(roomId, memberId);
            case CHAT_ROOM, NONE ->
                    findChatRoomMember(roomId, memberId);
        };

        if (!chatRoomMember.isParticipating()) {
            throw new CustomException(
                    ErrorCode.CHAT_ROOM_ACCESS_DENIED
            );
        }

        return new ChatRoomParticipationContext(
                chatRoom,
                member,
                chatRoomMember
        );
    }

    /**
     * 해당 채팅방이 참여가능한 채팅방인지 검증
     * @param chatRoom
     */
    private void validateJoinableChatRoom(ChatRoom chatRoom) {
        if (chatRoom.getDeletedAt() != null) {
            throw new CustomException(
                    ErrorCode.CHAT_ROOM_DELETED
            );
        }

        if (chatRoom.getType() != ChatRoomType.GROUP) {
            throw new CustomException(
                    ErrorCode.INVALID_CHAT_ROOM_TYPE
            );
        }
    }

    /**
     * 유저 엔티티 반환
     * @param memberId
     * @return 유저 정보(Entity)
     */
    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.MEMBER_NOT_FOUND
                        )
                );
    }

    /**
     * 채팅방 엔티티 반환
     * @param roomId
     * @return
     */
    private ChatRoom findChatRoom(Long roomId) {
        return chatRoomRepository.findById(roomId)
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.CHAT_ROOM_NOT_FOUND
                        )
                );
    }

    /**
     * 채팅방 엔티티 반환 및 ChatRoom row Lock
     * @param roomId
     * @return
     */
    private ChatRoom findChatRoomLock(Long roomId) {
        return chatRoomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.CHAT_ROOM_NOT_FOUND
                        )
                );
    }

    /**
     * 채팅방 멤버 엔티티 반환
     * @param roomId
     * @param memberId
     * @return
     */
    private ChatRoomMember findChatRoomMember(
            Long roomId,
            Long memberId
    ) {
        return chatRoomMemberRepository
                .findByChatRoomIdAndMemberId(roomId, memberId)
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.CHAT_ROOM_ACCESS_DENIED)
                );
    }

    /**
     * 채팅방 멤버 엔티티 반환 및 ChatRoomMember row Lock
     * @param roomId
     * @param memberId
     * @return
     */
    private ChatRoomMember findChatRoomMemberForUpdate(
            Long roomId,
            Long memberId
    ) {
        return chatRoomMemberRepository
                .findByChatRoomIdAndMemberIdForUpdate(roomId, memberId)
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.CHAT_ROOM_ACCESS_DENIED)
                );
    }

    /**
     * 해당 유저의 정지, 탈퇴, 이메일 인증에 대한 검증
     * @param member
     */
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
}
