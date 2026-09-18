package com.project.ChatProject.service;

import com.project.ChatProject.dto.ChatMessageEvent;
import com.project.ChatProject.dto.projection.ChatRoomUnreadCountProjection;
import com.project.ChatProject.dto.response.*;
import com.project.ChatProject.entity.ChatMessage;
import com.project.ChatProject.entity.ChatRoom;
import com.project.ChatProject.entity.ChatRoomMember;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.enums.*;
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
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private static final String NO_LOCK = "NO_LOCK";
    private static final String CHAT_ROOM_LOCK = "CHAT_ROOM";
    private static final String CHAT_ROOM_MEMBER_LOCK = "CHAT_ROOM_MEMBER";

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
        validateMember(member);

        List<ChatRoomMember> chatRoomMembers =
                chatRoomMemberRepository
                        .findAllActiveByMemberId(member.getId());

        Map<Long, Long> unreadCountByRoomId =
                chatRoomMemberRepository.findUnreadCountsByMemberId(memberId)
                        .stream()
                        .collect(Collectors.toMap(
                                ChatRoomUnreadCountProjection::getRoomId,
                                ChatRoomUnreadCountProjection::getUnreadCount
                        ));

        return chatRoomMembers.stream()
                .map(chatRoomMember -> {
                    Long roomId = chatRoomMember.getChatRoom().getId();

                    long unreadCount =
                            unreadCountByRoomId.getOrDefault(roomId, 0L);

                    return ChatRoomResponse.of(chatRoomMember, unreadCount);
                })
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
    public ChatMessageEvent join(Long memberId, Long roomId) {
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
        } else
            rejoin(existingMember.get());

        ChatMessage enterMessage =
                ChatMessage.createSystem(
                        chatRoom,
                        member.getNickname() + "님이 입장하였습니다."
                );
        chatMessageRepository.save(enterMessage);
        chatRoom.updateLastMessageAt(enterMessage.getCreatedAt());

        return ChatMessageEvent
                .created(ChatMessageResponse.from(enterMessage));
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
                requireParticipation(roomId, memberId, NO_LOCK);

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
    public ChatMessageEvent leave(Long roomId, Long memberId) {

        ChatRoomParticipationContext context =
                requireParticipation(roomId, memberId, CHAT_ROOM_LOCK);

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

        return ChatMessageEvent
                .created(ChatMessageResponse.from(leaveMessage));
    }

    @Transactional(readOnly = true)
    public List<ChatRoomMemberResponse> getMembers(
            Long roomId,
            Long memberId
    )
    {
        ChatRoomParticipationContext context =
                requireParticipation(roomId, memberId, NO_LOCK);

        List<ChatRoomMember> chatRoomMembers = chatRoomMemberRepository
                .findAllParticipatingByChatRoomId(
                        context.chatRoom.getId()
                );

        return chatRoomMembers.stream()
                .map(ChatRoomMemberResponse::from)
                .toList();
    }

    @Transactional
    public ChatMessageEvent transferOwnership(
            Long roomId,
            Long memberId,
            Long newOwnerMemberId)
    {
        if (memberId.equals(newOwnerMemberId))
            throw new CustomException(
                    ErrorCode.INVALID_OWNER_TRANSFER_TARGET
            );

        ChatRoomParticipationContext myContext
                = requireParticipation(roomId, memberId, CHAT_ROOM_LOCK);

        if (myContext.chatRoomMember.getRole()
                != ChatRoomMemberRole.OWNER) {
            throw new CustomException(
                    ErrorCode.CHAT_ROOM_OWNER_REQUIRED
            );
        }

        ChatRoomParticipationContext newOwnerContext
                = requireParticipation(roomId, newOwnerMemberId, NO_LOCK);

        // 방장 위임
        myContext.chatRoomMember
                .transferOwnershipTo(newOwnerContext.chatRoomMember);

        // 시스템 메세지 작성
        ChatMessage newOwnerMessage
                = ChatMessage.createSystem(
                        myContext.chatRoom,
                        newOwnerContext.member.getNickname()
                                + "님이 방장으로 위임되셨습니다."
        );

        chatMessageRepository.save(newOwnerMessage);
        myContext.chatRoom
                .updateLastMessageAt(newOwnerMessage.getCreatedAt());

        return ChatMessageEvent
                .created(ChatMessageResponse.from(newOwnerMessage));

    }

    @Transactional
    public void updateReadPosition(
            Long roomId,
            Long memberId,
            Long lastReadMessageId
    )
    {
        ChatRoomParticipationContext context
                = requireParticipation(roomId, memberId, CHAT_ROOM_MEMBER_LOCK);

        ChatRoomMember chatRoomMember = context.chatRoomMember;

        ChatMessage requestedLastMessage = chatMessageRepository
                .findByIdAndChatRoomId(lastReadMessageId, roomId)
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.CHAT_MESSAGE_NOT_FOUND
                        )
                );

        // joinedAt 이후가 아니면 예외
        if (requestedLastMessage.getCreatedAt()
                .isBefore(chatRoomMember.getJoinedAt()))
        {
            throw new CustomException(ErrorCode.CHAT_MESSAGE_NOT_FOUND);
        }

        ChatMessage currentLastMessage = chatRoomMember.getLastReadMessage();

        // 기존 읽음 위치와 같거나 이전 메세지라면 변경하지 않음
        if (currentLastMessage != null
                && !isAfter(
                        requestedLastMessage,
                        currentLastMessage
                )
        ) {
            return;
        }

        chatRoomMember.updateLastReadMessage(requestedLastMessage);
    }

    @Transactional
    public ChatMessageEvent deleteMessage(
            Long roomId,
            Long messageId,
            Long memberId
    ) {
        ChatRoomParticipationContext context
                = requireParticipation(roomId, memberId, NO_LOCK);

        ChatMessage message
                = findChatMessageForUpdate(messageId, roomId);

        // System Message 삭제 불가
        if (message.getType().equals(ChatMessageType.SYSTEM)) {
            throw new CustomException(
                    ErrorCode.SYSTEM_MESSAGE_DELETE_NOT_ALLOWED
            );
        }

        // 참가 이전 메세지 삭제 불가
        if (message.getCreatedAt()
                .isBefore(context.chatRoomMember.getJoinedAt())) {
            throw new CustomException(
                    ErrorCode.CHAT_MESSAGE_NOT_FOUND
            );
        }

        // 다른 유저의 메세지 삭제 불가
        Long contextMemberId = context.member.getId();
        Long messageSenderId = message.getSender().getId();
        if (!contextMemberId.equals(messageSenderId)) {
            throw new CustomException(
                    ErrorCode.CHAT_MESSAGE_DELETE_FORBIDDEN
            );
        }

        message.deleteMessage();
        ChatMessageResponse response =
                ChatMessageResponse.from(message);

        return ChatMessageEvent.deleted(response);
    }


    // == Private Method ==

    /**
     * 읽음 요청된 메세지가 이전에 읽었던 메세지보다 나중에 작성된 메세지인지 검증
     * @param request 읽음 요청된 메세지
     * @param current 실제 내가 읽은 채팅방 마지막 메세지
     * @return
     */
    private boolean isAfter(
            ChatMessage request,
            ChatMessage current
    )
    {
        int createdAtComparison = request.getCreatedAt()
                .compareTo(current.getCreatedAt());

        /*
            1 : request가 나중에 작성된 메세지
            0 : 작성시간이 동일한 메세지
           -1 : current보다 이전에 작성된 메세지
         */
        if (createdAtComparison != 0) {
            return createdAtComparison > 0;
        }

        return request.getId() > current.getId();
    }

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
     * 채팅방
     * @param roomId
     * @param memberId
     * @param lockType
     * @return
     */
    private ChatRoomParticipationContext requireParticipation(
            Long roomId,
            Long memberId,
            String lockType
    )
    {
        ChatRoom chatRoom = switch (lockType) {
            case CHAT_ROOM_LOCK -> findChatRoomLock(roomId);
            case CHAT_ROOM_MEMBER_LOCK, NO_LOCK -> findChatRoom(roomId);
            default -> throw new IllegalStateException(
                    "지원하지 않은 Lock type입니다. " + lockType
            );
        };
        validateJoinableChatRoom(chatRoom);

        Member member = findMember(memberId);
        validateMember(member);

        ChatRoomMember chatRoomMember = switch (lockType) {
            case CHAT_ROOM_MEMBER_LOCK ->
                    findChatRoomMemberForUpdate(roomId, memberId);
            case CHAT_ROOM_LOCK, NO_LOCK ->
                    findChatRoomMember(roomId, memberId);
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 Lock type입니다: " + lockType
            );
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
     * 채팅 메세지 엔티티 반환 및 ChatMessage row Lock
     * @param messageId
     * @param roomId
     * @return
     */
    private ChatMessage findChatMessageForUpdate(
            Long messageId,
            Long roomId
    ) {
        return chatMessageRepository
                .findByIdAndRoomIdForUpdate(messageId, roomId)
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.CHAT_MESSAGE_NOT_FOUND
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
