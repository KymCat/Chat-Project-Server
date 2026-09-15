package com.project.ChatProject.service;

import com.project.ChatProject.dto.response.*;
import com.project.ChatProject.entity.ChatMessage;
import com.project.ChatProject.entity.ChatRoom;
import com.project.ChatProject.entity.ChatRoomMember;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.enums.ChatRoomMemberRole;
import com.project.ChatProject.entity.enums.ChatRoomType;
import com.project.ChatProject.entity.enums.MemberStatus;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.repository.ChatMessageRepository;
import com.project.ChatProject.repository.ChatRoomMemberRepository;
import com.project.ChatProject.repository.ChatRoomRepository;
import com.project.ChatProject.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final MemberRepository memberRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Transactional
    public ChatRoomCreateResponse create(Long memberId, String name) {
        Member member = findMember(memberId);
        validateMember(member);

        ChatRoom chatRoom = ChatRoom.create(name);
        ChatRoomMember chatRoomMember = ChatRoomMember.create(chatRoom, member);

        chatRoomRepository.save(chatRoom);
        chatRoomMemberRepository.save(chatRoomMember);

        return new ChatRoomCreateResponse(
                chatRoom.getId(),
                chatRoom.getType(),
                chatRoom.getName()
        );
    }

    @Transactional(readOnly = true)
    public List<ChatRoomResponse> getChatRooms(Long memberId) {
        Member member = findMember(memberId);

        List<ChatRoomMember> lists = 
                chatRoomMemberRepository.findAllActiveByMemberId(member.getId());

        return lists.stream()
                        .map(ChatRoomResponse::of)
                        .toList();
    }

    @Transactional(readOnly = true)
    public List<GroupChatRoomResponse> getGroupChatRooms(Long memberId) {
        Member member = findMember(memberId);
        validateMember(member);

        List<ChatRoom> lists =
                chatRoomRepository.findAllGroupChatRoom(
                        memberId,
                        ChatRoomType.GROUP
                );

        return lists.stream()
                .map(GroupChatRoomResponse::of)
                .toList();
    }

    @Transactional
    public ChatRoomJoinResponse join(Long memberId, Long roomId) {
        Member member = findMember(memberId);
        validateMember(member);

        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.CHAT_ROOM_NOT_FOUND
                        )
                );
        validateJoinableChatRoom(chatRoom);

        Optional<ChatRoomMember> existingMember =
                chatRoomMemberRepository.findByChatRoomIdAndMemberId(
                        chatRoom.getId(),
                        memberId
                );

        if (existingMember.isEmpty()) {
            ChatRoomMember newMember
                    = ChatRoomMember.createMember(chatRoom, member);
            chatRoomMemberRepository.save(newMember);
        }
        else
            rejoin(existingMember.get());

        ChatMessage enterMessage =
                ChatMessage.createSystem(
                        chatRoom,
                        member.getNickname() + "님이 입장하였습니다."
                );
        chatMessageRepository.save(enterMessage);
        chatRoom.updateLastMessageAt(enterMessage.getCreatedAt());

        return new ChatRoomJoinResponse(
                GroupChatRoomResponse.of(chatRoom),
                ChatMessageResponse.from(enterMessage)
        );
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<ChatMessageResponse> getMessages(
            Long roomId,
            Long beforeMessageId,
            Long memberId,
            int size
    )
    {
        ChatRoomParticipationContext context =
                requireParticipation(roomId, memberId);

        ChatRoom chatRoom = context.chatRoom();
        ChatRoomMember chatRoomMember = context.chatRoomMember();

        List<ChatMessage> messages = findMessages(
                chatRoom.getId(),
                beforeMessageId,
                chatRoomMember.getJoinedAt(),
                size
        );

        boolean hasNext = messages.size() > size;
        if (hasNext) {
            messages = new ArrayList<>(messages.subList(0, size));
        }

        Long nextCursor = hasNext && !messages.isEmpty()
                ? messages.get(messages.size() - 1).getId()
                : null;

        List<ChatMessage> orderedMessages  = new ArrayList<>(messages);
        Collections.reverse(orderedMessages);

        List<ChatMessageResponse> content = orderedMessages.stream()
                .map(ChatMessageResponse::from)
                .toList();

        return CursorPageResponse.of(
                content,
                nextCursor,
                hasNext
        );
    }

    @Transactional
    public ChatMessageResponse leave(Long roomId, Long memberId) {

        ChatRoom lockedChatRoom = chatRoomRepository
                .findByIdForUpdate(roomId)
                .orElseThrow(()->
                        new CustomException(
                                ErrorCode.CHAT_ROOM_NOT_FOUND
                        )
                );

        ChatRoomParticipationContext context =
                requireParticipation(lockedChatRoom, memberId);

        ChatRoom chatRoom = context.chatRoom();
        Member member = context.member();
        ChatRoomMember chatRoomMember = context.chatRoomMember();

        // 채팅방 OWNER 나가기 방지
        if (chatRoomMember.getRole() == ChatRoomMemberRole.OWNER) {
            throw new CustomException(
                    ErrorCode.CHAT_ROOM_OWNER_TRANSFER_REQUIRED
            );
        }

        chatRoomMember.leave();
        ChatMessage leaveMessage =
                ChatMessage.createSystem(
                        chatRoom,
                        member.getNickname() + "님이 퇴장하였습니다."
                );
        chatMessageRepository.save(leaveMessage);
        chatRoom.updateLastMessageAt(leaveMessage.getCreatedAt());

        return ChatMessageResponse.from(leaveMessage);
    }

    @Transactional(readOnly = true)
    public List<ChatRoomMemberResponse> getMembers(
            Long roomId,
            Long memberId
    )
    {
        ChatRoomParticipationContext context =
                requireParticipation(roomId, memberId);

        List<ChatRoomMember> chatRoomMembers = chatRoomMemberRepository
                .findAllParticipatingByChatRoomId(
                        context.chatRoom.getId()
                );

        return chatRoomMembers.stream()
                .map(ChatRoomMemberResponse::from)
                .toList();
    }

    @Transactional
    public ChatMessageResponse transferOwnership(
            Long roomId,
            Long memberId,
            Long newOwnerMemberId)
    {
        if (memberId.equals(newOwnerMemberId))
            throw new CustomException(
                    ErrorCode.INVALID_OWNER_TRANSFER_TARGET
            );

        ChatRoom lockedChatRoom = chatRoomRepository
                .findByIdForUpdate(roomId)
                .orElseThrow(()->
                        new CustomException(
                                ErrorCode.CHAT_ROOM_NOT_FOUND
                        )
                );

        ChatRoomParticipationContext myContext
                = requireParticipation(lockedChatRoom, memberId);

        if (myContext.chatRoomMember.getRole()
                != ChatRoomMemberRole.OWNER) {
            throw new CustomException(
                    ErrorCode.CHAT_ROOM_OWNER_REQUIRED
            );
        }

        ChatRoomParticipationContext newOwnerContext
                = requireParticipation(
                lockedChatRoom,
                newOwnerMemberId
        );

        // 방장 위임
        myContext.chatRoomMember
                .transferOwnershipTo(newOwnerContext.chatRoomMember);

        // 시스템 메세지 작성
        ChatMessage newOwnerMessage
                = ChatMessage.createSystem(
                        lockedChatRoom,
                        newOwnerContext.member.getNickname()
                                + "님이 방장으로 위임되셨습니다."
        );

        chatMessageRepository.save(newOwnerMessage);
        lockedChatRoom.updateLastMessageAt(newOwnerMessage.getCreatedAt());

        return ChatMessageResponse
                .from(newOwnerMessage);

    }

    // == Private Method ==

    /**
     * 채팅방 서비스 로직에서 채팅방, 멤버, 채팅방 멤버 검증 반환 record
     */
    private record ChatRoomParticipationContext(
            ChatRoom chatRoom,
            Member member,
            ChatRoomMember chatRoomMember
    ) {
    }

    /**
     * 채팅방, 유저, 채팅방멤버에 대한 검증을 한번에 해결하는 로직
     * @param roomId
     * @param memberId
     * @return
     */
    private ChatRoomParticipationContext requireParticipation(
            Long roomId,
            Long memberId
    )
    {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(()->
                        new CustomException(
                                ErrorCode.CHAT_ROOM_NOT_FOUND
                        )
                );
        return requireParticipation(chatRoom, memberId);
    }

    /**
     * 채팅방, 유저, 채팅방멤버에 대한 검증을 한번에 해결하는 로직 (LOCK)
     * @param chatRoom : Lock 흭득을 위한 공통 진입점으로 사용할 수 있음
     * @param memberId
     * @return 채팅방, 유저, 채팅방멤버 객체 Context 반환
     */
    private ChatRoomParticipationContext requireParticipation(
            ChatRoom chatRoom,
            Long memberId
    )
    {
        validateJoinableChatRoom(chatRoom);

        Member member = findMember(memberId);
        validateMember(member);

        ChatRoomMember chatRoomMember = chatRoomMemberRepository
                .findByChatRoomIdAndMemberId(chatRoom.getId(), memberId)
                .orElseThrow(()->
                        new CustomException(
                                ErrorCode.CHAT_ROOM_ACCESS_DENIED
                        )
                );
        if (!chatRoomMember.isParticipating())
            throw new CustomException(
                    ErrorCode.CHAT_ROOM_ACCESS_DENIED
            );

        return new ChatRoomParticipationContext(
                chatRoom,
                member,
                chatRoomMember
        );
    }

    /**
     * 채팅방에서 메세지 조회
     * beforeMessageId의 null에 따라 조회방식을 구분
     *
     * @param chatRoomId
     * @param beforeMessageId
     * @param size
     * @return 조회된 채팅 메세지 반환
     */
    private List<ChatMessage> findMessages(
            Long chatRoomId,
            Long beforeMessageId,
            Instant joinedAt,
            int size
    )
    {
        Pageable pageable =
                PageRequest.of(0, size+1);

        Instant beforeCreatedAt = null;
        if (beforeMessageId != null) {
            ChatMessage beforeMessage =
                    chatMessageRepository
                            .findByIdAndChatRoomId(beforeMessageId, chatRoomId)
                            .orElseThrow(()->
                                    new CustomException(
                                            ErrorCode.CHAT_MESSAGE_NOT_FOUND
                                    )
                            );
            beforeCreatedAt = beforeMessage.getCreatedAt();
        }

        return beforeMessageId != null
                ? chatMessageRepository.findMessages(
                        chatRoomId,
                        beforeMessageId,
                        beforeCreatedAt,
                        joinedAt,
                        pageable
                )
                : chatMessageRepository.findLatestMessages(
                        chatRoomId,
                        joinedAt,
                        pageable
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
     * 한번 퇴장했던 채팅방으로 재참여
     * @param chatRoomMember
     */
    private void rejoin(ChatRoomMember chatRoomMember) {
        if (chatRoomMember.isParticipating())
            throw new CustomException(
                    ErrorCode.CHAT_ROOM_ALREADY_JOINED
            );

        chatRoomMember.rejoin();
    }

    /**
     * 유저 정보를 찾아서 반환
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
