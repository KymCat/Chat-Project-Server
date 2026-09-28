package com.project.ChatProject.service;

import com.project.ChatProject.dto.event.ChatMessageEvent;
import com.project.ChatProject.dto.event.ChatRoomEvent;
import com.project.ChatProject.dto.projection.ChatRoomUnreadCountProjection;
import com.project.ChatProject.dto.response.*;
import com.project.ChatProject.dto.result.ChatRoomNameUpdateResult;
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
import com.project.ChatProject.service.support.ChatRoomParticipationContext;
import com.project.ChatProject.service.support.ParticipationLockMode;
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

    private final ChatRoomParticipationService participation;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Transactional
    public ChatRoomCreateResponse create(Long memberId, String name) {
        Member member = participation.requireActiveMember(memberId);

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
        Member member = participation.requireActiveMember(memberId);

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
        Member member = participation.requireActiveMember(memberId);

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
        Member member = participation.requireActiveMember(memberId);

        ChatRoom chatRoom = participation.requireJoinableChatRoom(roomId);

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
        ChatRoomParticipationContext context = participation
                .requireParticipation(
                        roomId,
                        memberId,
                        ParticipationLockMode.NONE
                );

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

        ChatRoomParticipationContext context = participation
                .requireParticipation(
                        roomId,
                        memberId,
                        ParticipationLockMode.CHAT_ROOM
                );

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
        ChatRoomParticipationContext context = participation
                .requireParticipation(
                        roomId,
                        memberId,
                        ParticipationLockMode.NONE
                );

        List<ChatRoomMember> chatRoomMembers = chatRoomMemberRepository
                .findAllParticipatingByChatRoomId(
                        context.chatRoom().getId()
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

        ChatRoomParticipationContext myContext = participation
                .requireParticipation(
                        roomId,
                        memberId,
                        ParticipationLockMode.CHAT_ROOM
                );

        if (myContext.chatRoomMember().getRole()
                != ChatRoomMemberRole.OWNER) {
            throw new CustomException(
                    ErrorCode.CHAT_ROOM_OWNER_REQUIRED
            );
        }

        ChatRoomParticipationContext newOwnerContext = participation
                .requireParticipation(
                        roomId,
                        newOwnerMemberId,
                        ParticipationLockMode.NONE
                );

        // 방장 위임
        myContext.chatRoomMember()
                .transferOwnershipTo(newOwnerContext.chatRoomMember());

        // 시스템 메세지 작성
        ChatMessage newOwnerMessage
                = ChatMessage.createSystem(
                        myContext.chatRoom(),
                        newOwnerContext.member().getNickname()
                                + "님이 방장으로 위임되셨습니다."
        );

        chatMessageRepository.save(newOwnerMessage);
        myContext.chatRoom()
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
        ChatRoomParticipationContext context = participation
                .requireParticipation(
                        roomId,
                        memberId,
                        ParticipationLockMode.CHAT_ROOM_MEMBER
                );

        ChatRoomMember chatRoomMember = context.chatRoomMember();

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
        ChatRoomParticipationContext context = participation
                .requireParticipation(
                        roomId,
                        memberId,
                        ParticipationLockMode.NONE
                );

        ChatMessage message
                = findChatMessageForUpdate(messageId, roomId);

        // 일반 메세지가 아닌 경우, 삭제 불가
        if (message.getType() != ChatMessageType.TEXT) {
            throw new CustomException(
                    ErrorCode.CHAT_MESSAGE_TYPE_DELETE_NOT_ALLOWED
            );
        }

        // 참가 이전 메세지 삭제 불가
        if (message.getCreatedAt()
                .isBefore(context.chatRoomMember().getJoinedAt())) {
            throw new CustomException(
                    ErrorCode.CHAT_MESSAGE_NOT_FOUND
            );
        }

        // 다른 유저의 메세지 삭제 불가
        Long contextMemberId = context.member().getId();
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

    @Transactional
    public ChatMessageEvent editMessage(
            Long roomId,
            Long messageId,
            Long memberId,
            String content
    )
    {
        ChatRoomParticipationContext context = participation
                .requireParticipation(
                        roomId,
                        memberId,
                        ParticipationLockMode.NONE
                );

        ChatMessage message
                = findChatMessageForUpdate(messageId, roomId);

        // 일반 메세지가 아닌 경우, 수정 불가
        if (message.getType() != ChatMessageType.TEXT) {
            throw new CustomException(
                    ErrorCode.CHAT_MESSAGE_TYPE_EDIT_NOT_ALLOWED
            );
        }

        // 삭제된 메세지는 수정 불가
        if (message.getDeletedAt() != null) {
            throw new CustomException(
                    ErrorCode.CHAT_MESSAGE_NOT_FOUND
            );
        }

        // 참가 이전 메세지 수정 불가
        if (message.getCreatedAt()
                .isBefore(context.chatRoomMember().getJoinedAt())) {
            throw new CustomException(
                    ErrorCode.CHAT_MESSAGE_NOT_FOUND
            );
        }

        // 다른 유저의 메세지 수정 불가
        Long contextMemberId = context.member().getId();
        Long messageSenderId = message.getSender().getId();
        if (!contextMemberId.equals(messageSenderId)) {
            throw new CustomException(
                    ErrorCode.CHAT_MESSAGE_EDIT_FORBIDDEN
            );
        }

        message.editMessage(content);
        ChatMessageResponse response =
                ChatMessageResponse.from(message);

        return ChatMessageEvent.updated(response);
    }

    @Transactional
    public ChatRoomNameUpdateResult updateChatRoomName(
            Long roomId,
            Long memberId,
            String updateName
    )
    {
        ChatRoomParticipationContext context = participation
                .requireParticipation(
                        roomId,
                        memberId,
                        ParticipationLockMode.CHAT_ROOM
                );

        ChatRoomMemberRole role = context.chatRoomMember().getRole();
        if (!role.equals(ChatRoomMemberRole.OWNER)) {
            throw new CustomException(
                    ErrorCode.CHAT_ROOM_OWNER_REQUIRED
            );
        }

        context.chatRoom().updateName(updateName);

        ChatMessage systemMessage = ChatMessage.createSystem(
                context.chatRoom(),
                context.member().getNickname()
                        + "님이 채팅방 이름을 '"
                        + updateName
                        + "'(으)로 변경했습니다."
        );
        chatMessageRepository.save(systemMessage);
        context.chatRoom().updateLastMessageAt(systemMessage.getCreatedAt());

        return new ChatRoomNameUpdateResult(
                ChatRoomEvent.updated(roomId, updateName),
                ChatMessageEvent.created(
                        ChatMessageResponse.from(systemMessage)
                )
        );
    }

    @Transactional
    public ChatRoomEvent delete(Long roomId, Long memberId) {
        ChatRoomParticipationContext context = participation
                .requireParticipation(
                        roomId,
                        memberId,
                        ParticipationLockMode.CHAT_ROOM
                );

        if (!context.chatRoomMember().getRole()
                .equals(ChatRoomMemberRole.OWNER))
        {
            throw new CustomException(
                    ErrorCode.CHAT_ROOM_OWNER_REQUIRED
            );
        }

        String roomName = context.chatRoom().getName();
        context.chatRoom().delete();

        return ChatRoomEvent.deleted(roomId, roomName);
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
}
