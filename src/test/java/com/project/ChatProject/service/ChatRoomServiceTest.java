package com.project.ChatProject.service;

import com.project.ChatProject.dto.ChatMessageEvent;
import com.project.ChatProject.dto.projection.ChatRoomUnreadCountProjection;
import com.project.ChatProject.dto.response.ChatRoomCreateResponse;
import com.project.ChatProject.dto.response.ChatMessageResponse;
import com.project.ChatProject.dto.response.ChatRoomMemberResponse;
import com.project.ChatProject.dto.response.ChatRoomResponse;
import com.project.ChatProject.dto.response.CursorPageResponse;
import com.project.ChatProject.dto.response.GroupChatRoomResponse;
import com.project.ChatProject.entity.ChatMessage;
import com.project.ChatProject.entity.ChatRoom;
import com.project.ChatProject.entity.ChatRoomMember;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.enums.ChatMessageType;
import com.project.ChatProject.entity.enums.ChatMessageEventType;
import com.project.ChatProject.entity.enums.ChatRoomMemberRole;
import com.project.ChatProject.entity.enums.ChatRoomType;
import com.project.ChatProject.entity.enums.MemberStatus;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.repository.ChatMessageRepository;
import com.project.ChatProject.repository.ChatRoomMemberRepository;
import com.project.ChatProject.repository.ChatRoomRepository;
import com.project.ChatProject.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRoomServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @InjectMocks
    private ChatRoomService chatRoomService;

    @Test
    void createStoresGroupRoomAndCreatorAsOwner() {
        Member member = member(MemberStatus.ACTIVE, Instant.now());
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));
        when(chatRoomRepository.save(any(ChatRoom.class)))
                .thenAnswer(invocation -> {
                    ChatRoom chatRoom = invocation.getArgument(0);
                    ReflectionTestUtils.setField(chatRoom, "id", 10L);
                    return chatRoom;
                });

        ChatRoomCreateResponse response =
                chatRoomService.create(1L, "Backend");

        ArgumentCaptor<ChatRoom> chatRoomCaptor =
                ArgumentCaptor.forClass(ChatRoom.class);
        ArgumentCaptor<ChatRoomMember> roomMemberCaptor =
                ArgumentCaptor.forClass(ChatRoomMember.class);
        verify(chatRoomRepository).save(chatRoomCaptor.capture());
        verify(chatRoomMemberRepository).save(roomMemberCaptor.capture());

        ChatRoom savedRoom = chatRoomCaptor.getValue();
        assertThat(savedRoom.getId()).isEqualTo(10L);
        assertThat(savedRoom.getType()).isEqualTo(ChatRoomType.GROUP);
        assertThat(savedRoom.getName()).isEqualTo("Backend");
        assertThat(savedRoom.getDirectKey()).isNull();
        assertThat(savedRoom.getLastMessageAt()).isNull();
        assertThat(savedRoom.getDeletedAt()).isNull();

        ChatRoomMember savedRoomMember = roomMemberCaptor.getValue();
        assertThat(savedRoomMember.getChatRoom()).isSameAs(savedRoom);
        assertThat(savedRoomMember.getMember()).isSameAs(member);
        assertThat(savedRoomMember.getRole())
                .isEqualTo(ChatRoomMemberRole.OWNER);
        assertThat(savedRoomMember.getLastReadMessage()).isNull();
        assertThat(savedRoomMember.getJoinedAt()).isNotNull();
        assertThat(savedRoomMember.getLeftAt()).isNull();

        assertThat(response).isEqualTo(
                new ChatRoomCreateResponse(
                        10L,
                        ChatRoomType.GROUP,
                        "Backend"
                )
        );
    }

    @Test
    void createRejectsUnknownMember() {
        when(memberRepository.findById(1L))
                .thenReturn(Optional.empty());

        assertCreationRejected(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    void createRejectsSuspendedMember() {
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(
                        member(MemberStatus.SUSPENDED, Instant.now())
                ));

        assertCreationRejected(ErrorCode.MEMBER_BLOCKED);
    }

    @Test
    void createRejectsWithdrawnMember() {
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(
                        member(MemberStatus.WITHDRAWN, Instant.now())
                ));

        assertCreationRejected(ErrorCode.MEMBER_WITHDRAWN);
    }

    @Test
    void createRejectsMemberWhoseEmailIsNotVerified() {
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(
                        member(MemberStatus.ACTIVE, null)
                ));

        assertCreationRejected(ErrorCode.EMAIL_VERIFICATION_REQUIRED);
    }

    @Test
    void getChatRoomsCombinesUnreadCountsAndDefaultsMissingRoomToZero() {
        Member member = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom unreadRoom = chatRoom();
        ChatRoom readRoom = ChatRoom.create("Java");
        ReflectionTestUtils.setField(readRoom, "id", 11L);
        ChatRoomMember unreadRoomMember =
                ChatRoomMember.create(unreadRoom, member);
        ChatRoomMember readRoomMember =
                ChatRoomMember.createMember(readRoom, member);
        ChatRoomUnreadCountProjection unreadCount =
                mock(ChatRoomUnreadCountProjection.class);

        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));
        when(chatRoomMemberRepository.findAllActiveByMemberId(1L))
                .thenReturn(List.of(unreadRoomMember, readRoomMember));
        when(chatRoomMemberRepository.findUnreadCountsByMemberId(1L))
                .thenReturn(List.of(unreadCount));
        when(unreadCount.getRoomId()).thenReturn(10L);
        when(unreadCount.getUnreadCount()).thenReturn(3L);

        List<ChatRoomResponse> response = chatRoomService.getChatRooms(1L);

        assertThat(response)
                .extracting(ChatRoomResponse::roomId, ChatRoomResponse::unreadCount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(10L, 3L),
                        org.assertj.core.groups.Tuple.tuple(11L, 0L)
                );
    }

    @Test
    void getGroupChatRoomsReturnsAvailableRooms() {
        Instant lastMessageAt = Instant.parse("2026-09-11T01:00:00Z");
        Member member = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom activeRoom = ChatRoom.create("Backend");
        ChatRoom emptyRoom = ChatRoom.create("Java");
        ReflectionTestUtils.setField(activeRoom, "id", 10L);
        ReflectionTestUtils.setField(
                activeRoom,
                "lastMessageAt",
                lastMessageAt
        );
        ReflectionTestUtils.setField(emptyRoom, "id", 11L);

        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));
        when(chatRoomRepository.findAllGroupChatRoom(
                1L,
                ChatRoomType.GROUP
        )).thenReturn(List.of(activeRoom, emptyRoom));

        List<GroupChatRoomResponse> response =
                chatRoomService.getGroupChatRooms(1L);

        assertThat(response).containsExactly(
                new GroupChatRoomResponse(
                        10L,
                        "Backend",
                        lastMessageAt
                ),
                new GroupChatRoomResponse(
                        11L,
                        "Java",
                        null
                )
        );
        verify(chatRoomRepository).findAllGroupChatRoom(
                1L,
                ChatRoomType.GROUP
        );
    }

    @Test
    void getGroupChatRoomsRejectsUnknownMember() {
        when(memberRepository.findById(1L))
                .thenReturn(Optional.empty());

        assertAvailableRoomsRejected(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    void getGroupChatRoomsRejectsSuspendedMember() {
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(
                        member(MemberStatus.SUSPENDED, Instant.now())
                ));

        assertAvailableRoomsRejected(ErrorCode.MEMBER_BLOCKED);
    }

    @Test
    void getGroupChatRoomsRejectsWithdrawnMember() {
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(
                        member(MemberStatus.WITHDRAWN, Instant.now())
                ));

        assertAvailableRoomsRejected(ErrorCode.MEMBER_WITHDRAWN);
    }

    @Test
    void getGroupChatRoomsRejectsMemberWhoseEmailIsNotVerified() {
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(
                        member(MemberStatus.ACTIVE, null)
                ));

        assertAvailableRoomsRejected(ErrorCode.EMAIL_VERIFICATION_REQUIRED);
    }

    @Test
    void joinStoresNewMemberAndSystemMessage() {
        Instant createdAt = Instant.parse("2026-09-12T06:00:00Z");
        Member member = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom chatRoom = chatRoom();

        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));
        when(chatRoomRepository.findById(10L))
                .thenReturn(Optional.of(chatRoom));
        when(chatRoomMemberRepository.findByChatRoomIdAndMemberId(10L, 1L))
                .thenReturn(Optional.empty());
        stubSavedMessage(createdAt);

        ChatMessageEvent event = chatRoomService.join(1L, 10L);

        ArgumentCaptor<ChatRoomMember> memberCaptor =
                ArgumentCaptor.forClass(ChatRoomMember.class);
        ArgumentCaptor<ChatMessage> messageCaptor =
                ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatRoomMemberRepository).save(memberCaptor.capture());
        verify(chatMessageRepository).save(messageCaptor.capture());

        ChatRoomMember savedMember = memberCaptor.getValue();
        assertThat(savedMember.getChatRoom()).isSameAs(chatRoom);
        assertThat(savedMember.getMember()).isSameAs(member);
        assertThat(savedMember.getRole()).isEqualTo(ChatRoomMemberRole.MEMBER);
        assertThat(savedMember.isParticipating()).isTrue();

        ChatMessage savedMessage = messageCaptor.getValue();
        assertThat(savedMessage.getChatRoom()).isSameAs(chatRoom);
        assertThat(savedMessage.getSender()).isNull();
        assertThat(savedMessage.getClientMessageId()).isNull();
        assertThat(savedMessage.getType()).isEqualTo(ChatMessageType.SYSTEM);
        assertThat(savedMessage.getContent()).isEqualTo("사용자님이 입장하였습니다.");
        assertThat(chatRoom.getLastMessageAt()).isEqualTo(createdAt);

        assertThat(event.eventType()).isEqualTo(ChatMessageEventType.CREATED);
        assertThat(event.message().messageId()).isEqualTo(100L);
        assertThat(event.message().senderId()).isNull();
        assertThat(event.message().senderNickname()).isNull();
        assertThat(event.message().type()).isEqualTo(ChatMessageType.SYSTEM);
        assertThat(event.message().createdAt()).isEqualTo(createdAt);
    }

    @Test
    void joinReactivatesFormerMemberAndStoresSystemMessage() {
        Instant createdAt = Instant.parse("2026-09-12T06:00:00Z");
        Member member = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom chatRoom = chatRoom();
        ChatRoomMember formerMember = ChatRoomMember.createMember(chatRoom, member);
        ReflectionTestUtils.setField(formerMember, "leftAt", Instant.now());
        ReflectionTestUtils.setField(
                formerMember,
                "lastReadMessage",
                ChatMessage.createText(chatRoom, member, "이전 메시지")
        );

        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));
        when(chatRoomRepository.findById(10L))
                .thenReturn(Optional.of(chatRoom));
        when(chatRoomMemberRepository.findByChatRoomIdAndMemberId(10L, 1L))
                .thenReturn(Optional.of(formerMember));
        stubSavedMessage(createdAt);

        ChatMessageEvent event = chatRoomService.join(1L, 10L);

        assertThat(formerMember.isParticipating()).isTrue();
        assertThat(formerMember.getLastReadMessage()).isNull();
        verify(chatRoomMemberRepository, never()).save(any(ChatRoomMember.class));
        verify(chatMessageRepository).save(any(ChatMessage.class));
        assertThat(event.eventType()).isEqualTo(ChatMessageEventType.CREATED);
        assertThat(event.message().type()).isEqualTo(ChatMessageType.SYSTEM);
    }

    @Test
    void joinRejectsActiveMemberWithoutStoringSystemMessage() {
        Member member = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom chatRoom = chatRoom();
        ChatRoomMember activeMember = ChatRoomMember.createMember(chatRoom, member);

        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));
        when(chatRoomRepository.findById(10L))
                .thenReturn(Optional.of(chatRoom));
        when(chatRoomMemberRepository.findByChatRoomIdAndMemberId(10L, 1L))
                .thenReturn(Optional.of(activeMember));

        assertThatThrownBy(() -> chatRoomService.join(1L, 10L))
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHAT_ROOM_ALREADY_JOINED)
                );

        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void getMessagesReturnsLatestPageInChronologicalOrder() {
        Member member = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom chatRoom = chatRoom();
        ChatRoomMember roomMember = ChatRoomMember.createMember(chatRoom, member);
        List<ChatMessage> queriedMessages = List.of(
                message(chatRoom, member, 104L, "네 번째", "2026-09-14T04:00:00Z"),
                message(chatRoom, member, 103L, "세 번째", "2026-09-14T03:00:00Z"),
                message(chatRoom, member, 102L, "두 번째", "2026-09-14T02:00:00Z"),
                message(chatRoom, member, 101L, "첫 번째", "2026-09-14T01:00:00Z")
        );

        stubMessageAccess(member, chatRoom, roomMember);
        when(chatMessageRepository.findLatestMessages(
                eq(10L),
                eq(roomMember.getJoinedAt()),
                any(Pageable.class)
        ))
                .thenReturn(queriedMessages);

        CursorPageResponse<ChatMessageResponse> response =
                chatRoomService.getMessages(10L, null, 1L, 3);

        assertThat(response.content())
                .extracting(ChatMessageResponse::messageId)
                .containsExactly(102L, 103L, 104L);
        assertThat(response.nextCursor()).isEqualTo(102L);
        assertThat(response.hasNext()).isTrue();

        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);
        verify(chatMessageRepository)
                .findLatestMessages(
                        eq(10L),
                        eq(roomMember.getJoinedAt()),
                        pageableCaptor.capture()
                );
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(4);
    }

    @Test
    void getMessagesUsesMessageCursorForNextPage() {
        Member member = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom chatRoom = chatRoom();
        ChatRoomMember roomMember = ChatRoomMember.createMember(chatRoom, member);
        ChatMessage cursorMessage = message(
                chatRoom,
                member,
                104L,
                "커서",
                "2026-09-14T04:00:00Z"
        );
        List<ChatMessage> queriedMessages = List.of(
                message(chatRoom, member, 103L, "세 번째", "2026-09-14T03:00:00Z"),
                message(chatRoom, member, 102L, "두 번째", "2026-09-14T02:00:00Z")
        );

        stubMessageAccess(member, chatRoom, roomMember);
        when(chatMessageRepository.findByIdAndChatRoomId(104L, 10L))
                .thenReturn(Optional.of(cursorMessage));
        when(chatMessageRepository.findMessages(
                eq(10L),
                eq(104L),
                eq(cursorMessage.getCreatedAt()),
                eq(roomMember.getJoinedAt()),
                any(Pageable.class)
        )).thenReturn(queriedMessages);

        CursorPageResponse<ChatMessageResponse> response =
                chatRoomService.getMessages(10L, 104L, 1L, 3);

        assertThat(response.content())
                .extracting(ChatMessageResponse::messageId)
                .containsExactly(102L, 103L);
        assertThat(response.nextCursor()).isNull();
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void getMessagesRejectsFormerRoomMember() {
        Member member = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom chatRoom = chatRoom();
        ChatRoomMember formerMember = ChatRoomMember.createMember(chatRoom, member);
        ReflectionTestUtils.setField(formerMember, "leftAt", Instant.now());

        stubMessageAccess(member, chatRoom, formerMember);

        assertThatThrownBy(() -> chatRoomService.getMessages(10L, null, 1L, 30))
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHAT_ROOM_ACCESS_DENIED)
                );

        verify(chatMessageRepository, never())
                .findLatestMessages(
                        any(Long.class),
                        any(Instant.class),
                        any(Pageable.class)
                );
    }

    @Test
    void getMessagesMasksDeletedMessageContent() {
        Member member = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom chatRoom = chatRoom();
        ChatRoomMember roomMember = ChatRoomMember.createMember(chatRoom, member);
        ChatMessage deletedMessage = message(
                chatRoom,
                member,
                101L,
                "삭제 전 내용",
                "2026-09-16T01:00:00Z"
        );
        ReflectionTestUtils.setField(deletedMessage, "deletedAt", Instant.now());

        stubMessageAccess(member, chatRoom, roomMember);
        when(chatMessageRepository.findLatestMessages(
                eq(10L),
                eq(roomMember.getJoinedAt()),
                any(Pageable.class)
        )).thenReturn(List.of(deletedMessage));

        ChatMessageResponse response = chatRoomService
                .getMessages(10L, null, 1L, 30)
                .content()
                .get(0);

        assertThat(response.deleted()).isTrue();
        assertThat(response.content()).isNull();
    }

    @Test
    void updateReadPositionAdvancesToRequestedMessage() {
        Member member = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom chatRoom = chatRoom();
        ChatRoomMember roomMember = ChatRoomMember.createMember(chatRoom, member);
        ReflectionTestUtils.setField(
                roomMember,
                "joinedAt",
                Instant.parse("2026-09-16T00:00:00Z")
        );
        ChatMessage currentMessage = message(
                chatRoom,
                member,
                101L,
                "현재 읽음 위치",
                "2026-09-16T01:00:00Z"
        );
        ChatMessage requestedMessage = message(
                chatRoom,
                member,
                102L,
                "새 읽음 위치",
                "2026-09-16T02:00:00Z"
        );
        ReflectionTestUtils.setField(roomMember, "lastReadMessage", currentMessage);

        stubReadPositionAccess(member, chatRoom, roomMember);
        when(chatMessageRepository.findByIdAndChatRoomId(102L, 10L))
                .thenReturn(Optional.of(requestedMessage));

        chatRoomService.updateReadPosition(10L, 1L, 102L);

        assertThat(roomMember.getLastReadMessage()).isSameAs(requestedMessage);
        verify(chatRoomMemberRepository)
                .findByChatRoomIdAndMemberIdForUpdate(10L, 1L);
    }

    @Test
    void updateReadPositionDoesNotMoveBackward() {
        Member member = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom chatRoom = chatRoom();
        ChatRoomMember roomMember = ChatRoomMember.createMember(chatRoom, member);
        ReflectionTestUtils.setField(
                roomMember,
                "joinedAt",
                Instant.parse("2026-09-16T00:00:00Z")
        );
        ChatMessage requestedMessage = message(
                chatRoom,
                member,
                101L,
                "과거 메시지",
                "2026-09-16T01:00:00Z"
        );
        ChatMessage currentMessage = message(
                chatRoom,
                member,
                102L,
                "현재 읽음 위치",
                "2026-09-16T02:00:00Z"
        );
        ReflectionTestUtils.setField(roomMember, "lastReadMessage", currentMessage);

        stubReadPositionAccess(member, chatRoom, roomMember);
        when(chatMessageRepository.findByIdAndChatRoomId(101L, 10L))
                .thenReturn(Optional.of(requestedMessage));

        chatRoomService.updateReadPosition(10L, 1L, 101L);

        assertThat(roomMember.getLastReadMessage()).isSameAs(currentMessage);
    }

    @Test
    void updateReadPositionRejectsMessageBeforeJoinedAt() {
        Member member = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom chatRoom = chatRoom();
        ChatRoomMember roomMember = ChatRoomMember.createMember(chatRoom, member);
        ReflectionTestUtils.setField(
                roomMember,
                "joinedAt",
                Instant.parse("2026-09-16T02:00:00Z")
        );
        ChatMessage requestedMessage = message(
                chatRoom,
                member,
                101L,
                "참여 전 메시지",
                "2026-09-16T01:00:00Z"
        );

        stubReadPositionAccess(member, chatRoom, roomMember);
        when(chatMessageRepository.findByIdAndChatRoomId(101L, 10L))
                .thenReturn(Optional.of(requestedMessage));

        assertThatThrownBy(() ->
                chatRoomService.updateReadPosition(10L, 1L, 101L)
        ).isInstanceOfSatisfying(
                CustomException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_MESSAGE_NOT_FOUND)
        );

        assertThat(roomMember.getLastReadMessage()).isNull();
    }

    @Test
    void getMessagesRejectsCursorFromAnotherRoom() {
        Member member = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom chatRoom = chatRoom();
        ChatRoomMember roomMember = ChatRoomMember.createMember(chatRoom, member);

        stubMessageAccess(member, chatRoom, roomMember);
        when(chatMessageRepository.findByIdAndChatRoomId(999L, 10L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatRoomService.getMessages(10L, 999L, 1L, 30))
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHAT_MESSAGE_NOT_FOUND)
                );

        verify(chatMessageRepository, never()).findMessages(
                any(Long.class),
                any(Long.class),
                any(Instant.class),
                any(Instant.class),
                any(Pageable.class)
        );
    }

    @Test
    void leaveUpdatesMembershipAndStoresSystemMessage() {
        Instant createdAt = Instant.parse("2026-09-14T09:00:00Z");
        Member member = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom chatRoom = chatRoom();
        ChatRoomMember roomMember = ChatRoomMember.createMember(chatRoom, member);

        stubLockedMessageAccess(member, chatRoom, roomMember);
        stubSavedMessage(createdAt);

        ChatMessageEvent event = chatRoomService.leave(10L, 1L);
        ChatMessageResponse response = event.message();

        assertThat(roomMember.isParticipating()).isFalse();

        ArgumentCaptor<ChatMessage> messageCaptor =
                ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(messageCaptor.capture());

        ChatMessage savedMessage = messageCaptor.getValue();
        assertThat(savedMessage.getChatRoom()).isSameAs(chatRoom);
        assertThat(savedMessage.getSender()).isNull();
        assertThat(savedMessage.getType()).isEqualTo(ChatMessageType.SYSTEM);
        assertThat(savedMessage.getContent()).isEqualTo("사용자님이 퇴장하였습니다.");
        assertThat(chatRoom.getLastMessageAt()).isEqualTo(createdAt);

        assertThat(event.eventType()).isEqualTo(ChatMessageEventType.CREATED);
        assertThat(response.messageId()).isEqualTo(100L);
        assertThat(response.roomId()).isEqualTo(10L);
        assertThat(response.senderId()).isNull();
        assertThat(response.senderNickname()).isNull();
        assertThat(response.type()).isEqualTo(ChatMessageType.SYSTEM);
        assertThat(response.content()).isEqualTo("사용자님이 퇴장하였습니다.");
        assertThat(response.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void leaveRejectsOwnerWithoutStoringSystemMessage() {
        Member member = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom chatRoom = chatRoom();
        ChatRoomMember owner = ChatRoomMember.create(chatRoom, member);

        stubLockedMessageAccess(member, chatRoom, owner);

        assertThatThrownBy(() -> chatRoomService.leave(10L, 1L))
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHAT_ROOM_OWNER_TRANSFER_REQUIRED)
                );

        assertThat(owner.isParticipating()).isTrue();
        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void leaveRejectsFormerRoomMemberWithoutStoringSystemMessage() {
        Member member = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom chatRoom = chatRoom();
        ChatRoomMember formerMember = ChatRoomMember.createMember(chatRoom, member);
        ReflectionTestUtils.setField(formerMember, "leftAt", Instant.now());

        stubLockedMessageAccess(member, chatRoom, formerMember);

        assertThatThrownBy(() -> chatRoomService.leave(10L, 1L))
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHAT_ROOM_ACCESS_DENIED)
                );

        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void transferOwnershipChangesOwnerAndMemberRoles() {
        Instant createdAt = Instant.parse("2026-09-15T10:00:00Z");
        Member ownerMember = member(MemberStatus.ACTIVE, Instant.now());
        Member newOwnerMember = member(2L, "새방장", MemberStatus.ACTIVE);
        ReflectionTestUtils.setField(
                newOwnerMember,
                "emailVerifiedAt",
                Instant.now()
        );
        ChatRoom chatRoom = chatRoom();
        ChatRoomMember owner = ChatRoomMember.create(chatRoom, ownerMember);
        ChatRoomMember newOwner =
                ChatRoomMember.createMember(chatRoom, newOwnerMember);

        when(chatRoomRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(chatRoom));
        when(chatRoomRepository.findById(10L))
                .thenReturn(Optional.of(chatRoom));
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(ownerMember));
        when(memberRepository.findById(2L))
                .thenReturn(Optional.of(newOwnerMember));
        when(chatRoomMemberRepository.findByChatRoomIdAndMemberId(10L, 1L))
                .thenReturn(Optional.of(owner));
        when(chatRoomMemberRepository.findByChatRoomIdAndMemberId(10L, 2L))
                .thenReturn(Optional.of(newOwner));
        stubSavedMessage(createdAt);

        ChatMessageEvent event =
                chatRoomService.transferOwnership(10L, 1L, 2L);
        ChatMessageResponse response = event.message();

        assertThat(owner.getRole()).isEqualTo(ChatRoomMemberRole.MEMBER);
        assertThat(newOwner.getRole()).isEqualTo(ChatRoomMemberRole.OWNER);
        assertThat(chatRoom.getLastMessageAt()).isEqualTo(createdAt);

        ArgumentCaptor<ChatMessage> messageCaptor =
                ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(messageCaptor.capture());

        ChatMessage savedMessage = messageCaptor.getValue();
        assertThat(savedMessage.getChatRoom()).isSameAs(chatRoom);
        assertThat(savedMessage.getSender()).isNull();
        assertThat(savedMessage.getClientMessageId()).isNull();
        assertThat(savedMessage.getType()).isEqualTo(ChatMessageType.SYSTEM);
        assertThat(savedMessage.getContent())
                .isEqualTo("새방장님이 방장으로 위임되셨습니다.");

        assertThat(event.eventType()).isEqualTo(ChatMessageEventType.CREATED);
        assertThat(response.messageId()).isEqualTo(100L);
        assertThat(response.roomId()).isEqualTo(10L);
        assertThat(response.senderId()).isNull();
        assertThat(response.senderNickname()).isNull();
        assertThat(response.type()).isEqualTo(ChatMessageType.SYSTEM);
        assertThat(response.content())
                .isEqualTo("새방장님이 방장으로 위임되셨습니다.");
        assertThat(response.createdAt()).isEqualTo(createdAt);
        verify(chatRoomRepository).findByIdForUpdate(10L);
    }

    @Test
    void transferOwnershipRejectsSameMemberWithoutLockingRoom() {
        assertThatThrownBy(() ->
                chatRoomService.transferOwnership(10L, 1L, 1L)
        )
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_OWNER_TRANSFER_TARGET)
                );

        verify(chatRoomRepository, never()).findByIdForUpdate(any(Long.class));
    }

    @Test
    void transferOwnershipRejectsRequestFromMember() {
        Member requester = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom chatRoom = chatRoom();
        ChatRoomMember requesterParticipation =
                ChatRoomMember.createMember(chatRoom, requester);

        when(chatRoomRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(chatRoom));
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(requester));
        when(chatRoomMemberRepository.findByChatRoomIdAndMemberId(10L, 1L))
                .thenReturn(Optional.of(requesterParticipation));

        assertThatThrownBy(() ->
                chatRoomService.transferOwnership(10L, 1L, 2L)
        )
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHAT_ROOM_OWNER_REQUIRED)
                );

        assertThat(requesterParticipation.getRole())
                .isEqualTo(ChatRoomMemberRole.MEMBER);
        verify(memberRepository, never()).findById(2L);
        verify(chatRoomMemberRepository, never())
                .findByChatRoomIdAndMemberId(10L, 2L);
        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void transferOwnershipRejectsFormerRoomMemberAsTarget() {
        Member ownerMember = member(MemberStatus.ACTIVE, Instant.now());
        Member targetMember = member(2L, "이전멤버", MemberStatus.ACTIVE);
        ReflectionTestUtils.setField(
                targetMember,
                "emailVerifiedAt",
                Instant.now()
        );
        ChatRoom chatRoom = chatRoom();
        ChatRoomMember owner = ChatRoomMember.create(chatRoom, ownerMember);
        ChatRoomMember formerMember =
                ChatRoomMember.createMember(chatRoom, targetMember);
        ReflectionTestUtils.setField(formerMember, "leftAt", Instant.now());

        when(chatRoomRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(chatRoom));
        when(chatRoomRepository.findById(10L))
                .thenReturn(Optional.of(chatRoom));
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(ownerMember));
        when(memberRepository.findById(2L))
                .thenReturn(Optional.of(targetMember));
        when(chatRoomMemberRepository.findByChatRoomIdAndMemberId(10L, 1L))
                .thenReturn(Optional.of(owner));
        when(chatRoomMemberRepository.findByChatRoomIdAndMemberId(10L, 2L))
                .thenReturn(Optional.of(formerMember));

        assertThatThrownBy(() ->
                chatRoomService.transferOwnership(10L, 1L, 2L)
        )
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHAT_ROOM_ACCESS_DENIED)
                );

        assertThat(owner.getRole()).isEqualTo(ChatRoomMemberRole.OWNER);
        assertThat(formerMember.getRole()).isEqualTo(ChatRoomMemberRole.MEMBER);
    }

    @Test
    void deleteMessageMarksOwnTextMessageDeletedAndReturnsDeletedEvent() {
        Member member = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom chatRoom = chatRoom();
        ChatRoomMember roomMember = ChatRoomMember.createMember(chatRoom, member);
        ReflectionTestUtils.setField(
                roomMember,
                "joinedAt",
                Instant.parse("2026-09-16T00:00:00Z")
        );
        ChatMessage message = message(
                chatRoom,
                member,
                100L,
                "삭제할 메시지",
                "2026-09-16T01:00:00Z"
        );

        stubMessageAccess(member, chatRoom, roomMember);
        when(chatMessageRepository.findByIdAndRoomIdForUpdate(100L, 10L))
                .thenReturn(Optional.of(message));

        ChatMessageEvent event = chatRoomService.deleteMessage(10L, 100L, 1L);

        assertThat(event.eventType()).isEqualTo(ChatMessageEventType.DELETED);
        assertThat(event.message().messageId()).isEqualTo(100L);
        assertThat(event.message().content()).isNull();
        assertThat(event.message().deleted()).isTrue();
        assertThat(message.getDeletedAt()).isNotNull();
    }

    @Test
    void deleteMessageIsIdempotentAndKeepsInitialDeletedAt() {
        Member member = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom chatRoom = chatRoom();
        ChatRoomMember roomMember = ChatRoomMember.createMember(chatRoom, member);
        ReflectionTestUtils.setField(
                roomMember,
                "joinedAt",
                Instant.parse("2026-09-16T00:00:00Z")
        );
        ChatMessage message = message(
                chatRoom,
                member,
                100L,
                "삭제할 메시지",
                "2026-09-16T01:00:00Z"
        );

        stubMessageAccess(member, chatRoom, roomMember);
        when(chatMessageRepository.findByIdAndRoomIdForUpdate(100L, 10L))
                .thenReturn(Optional.of(message));

        ChatMessageEvent firstEvent =
                chatRoomService.deleteMessage(10L, 100L, 1L);
        Instant firstDeletedAt = message.getDeletedAt();
        ChatMessageEvent secondEvent =
                chatRoomService.deleteMessage(10L, 100L, 1L);

        assertThat(firstEvent.eventType()).isEqualTo(ChatMessageEventType.DELETED);
        assertThat(secondEvent.eventType()).isEqualTo(ChatMessageEventType.DELETED);
        assertThat(message.getDeletedAt()).isEqualTo(firstDeletedAt);
    }

    @Test
    void deleteMessageRejectsSystemMessage() {
        Member member = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom chatRoom = chatRoom();
        ChatRoomMember roomMember = ChatRoomMember.createMember(chatRoom, member);
        ChatMessage systemMessage = ChatMessage.createSystem(
                chatRoom,
                "사용자님이 입장하였습니다."
        );
        ReflectionTestUtils.setField(systemMessage, "id", 100L);
        ReflectionTestUtils.setField(
                systemMessage,
                "createdAt",
                Instant.now()
        );

        stubMessageAccess(member, chatRoom, roomMember);
        when(chatMessageRepository.findByIdAndRoomIdForUpdate(100L, 10L))
                .thenReturn(Optional.of(systemMessage));

        assertThatThrownBy(() ->
                chatRoomService.deleteMessage(10L, 100L, 1L)
        ).isInstanceOfSatisfying(
                CustomException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_MESSAGE_TYPE_DELETE_NOT_ALLOWED)
        );

        assertThat(systemMessage.getDeletedAt()).isNull();
    }

    @Test
    void deleteMessageRejectsMessageSentByAnotherMember() {
        Member requester = member(MemberStatus.ACTIVE, Instant.now());
        Member sender = member(2L, "다른사용자", MemberStatus.ACTIVE);
        ChatRoom chatRoom = chatRoom();
        ChatRoomMember roomMember = ChatRoomMember.createMember(chatRoom, requester);
        ReflectionTestUtils.setField(
                roomMember,
                "joinedAt",
                Instant.parse("2026-09-16T00:00:00Z")
        );
        ChatMessage message = message(
                chatRoom,
                sender,
                100L,
                "다른 사용자의 메시지",
                "2026-09-16T01:00:00Z"
        );

        stubMessageAccess(requester, chatRoom, roomMember);
        when(chatMessageRepository.findByIdAndRoomIdForUpdate(100L, 10L))
                .thenReturn(Optional.of(message));

        assertThatThrownBy(() ->
                chatRoomService.deleteMessage(10L, 100L, 1L)
        ).isInstanceOfSatisfying(
                CustomException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_MESSAGE_DELETE_FORBIDDEN)
        );

        assertThat(message.getDeletedAt()).isNull();
    }

    @Test
    void deleteMessageHidesMessageCreatedBeforeCurrentParticipation() {
        Member member = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom chatRoom = chatRoom();
        ChatRoomMember roomMember = ChatRoomMember.createMember(chatRoom, member);
        ReflectionTestUtils.setField(
                roomMember,
                "joinedAt",
                Instant.parse("2026-09-16T02:00:00Z")
        );
        ChatMessage message = message(
                chatRoom,
                member,
                100L,
                "참여 이전 메시지",
                "2026-09-16T01:00:00Z"
        );

        stubMessageAccess(member, chatRoom, roomMember);
        when(chatMessageRepository.findByIdAndRoomIdForUpdate(100L, 10L))
                .thenReturn(Optional.of(message));

        assertThatThrownBy(() ->
                chatRoomService.deleteMessage(10L, 100L, 1L)
        ).isInstanceOfSatisfying(
                CustomException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_MESSAGE_NOT_FOUND)
        );

        assertThat(message.getDeletedAt()).isNull();
    }

    @Test
    void editMessageUpdatesOwnTextMessageAndReturnsUpdatedEvent() {
        Member member = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom chatRoom = chatRoom();
        ChatRoomMember roomMember = ChatRoomMember.createMember(chatRoom, member);
        ReflectionTestUtils.setField(
                roomMember,
                "joinedAt",
                Instant.parse("2026-09-16T00:00:00Z")
        );
        ChatMessage message = message(
                chatRoom,
                member,
                100L,
                "수정 전 메시지",
                "2026-09-16T01:00:00Z"
        );

        stubMessageAccess(member, chatRoom, roomMember);
        when(chatMessageRepository.findByIdAndRoomIdForUpdate(100L, 10L))
                .thenReturn(Optional.of(message));

        ChatMessageEvent event = chatRoomService.editMessage(
                10L,
                100L,
                1L,
                "  수정된 메시지  "
        );

        assertThat(message.getContent()).isEqualTo("수정된 메시지");
        assertThat(message.getEditedAt()).isNotNull();
        assertThat(event.eventType()).isEqualTo(ChatMessageEventType.UPDATED);
        assertThat(event.message().messageId()).isEqualTo(100L);
        assertThat(event.message().content()).isEqualTo("수정된 메시지");
        assertThat(event.message().editedAt()).isEqualTo(message.getEditedAt());
        assertThat(event.message().deleted()).isFalse();
        verify(chatMessageRepository).findByIdAndRoomIdForUpdate(100L, 10L);
    }

    @Test
    void editMessageRejectsSystemMessage() {
        Member member = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom chatRoom = chatRoom();
        ChatRoomMember roomMember = ChatRoomMember.createMember(chatRoom, member);
        ChatMessage systemMessage = ChatMessage.createSystem(
                chatRoom,
                "사용자님이 입장하였습니다."
        );
        ReflectionTestUtils.setField(systemMessage, "id", 100L);
        ReflectionTestUtils.setField(systemMessage, "createdAt", Instant.now());

        stubMessageAccess(member, chatRoom, roomMember);
        when(chatMessageRepository.findByIdAndRoomIdForUpdate(100L, 10L))
                .thenReturn(Optional.of(systemMessage));

        assertThatThrownBy(() ->
                chatRoomService.editMessage(10L, 100L, 1L, "수정 내용")
        ).isInstanceOfSatisfying(
                CustomException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_MESSAGE_TYPE_EDIT_NOT_ALLOWED)
        );

        assertThat(systemMessage.getEditedAt()).isNull();
    }

    @Test
    void editMessageRejectsDeletedMessage() {
        Member member = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom chatRoom = chatRoom();
        ChatRoomMember roomMember = ChatRoomMember.createMember(chatRoom, member);
        ChatMessage message = message(
                chatRoom,
                member,
                100L,
                "삭제된 메시지",
                "2026-09-16T01:00:00Z"
        );
        message.deleteMessage();

        stubMessageAccess(member, chatRoom, roomMember);
        when(chatMessageRepository.findByIdAndRoomIdForUpdate(100L, 10L))
                .thenReturn(Optional.of(message));

        assertThatThrownBy(() ->
                chatRoomService.editMessage(10L, 100L, 1L, "수정 내용")
        ).isInstanceOfSatisfying(
                CustomException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_MESSAGE_NOT_FOUND)
        );

        assertThat(message.getEditedAt()).isNull();
    }

    @Test
    void editMessageRejectsMessageSentByAnotherMember() {
        Member requester = member(MemberStatus.ACTIVE, Instant.now());
        Member sender = member(2L, "다른사용자", MemberStatus.ACTIVE);
        ChatRoom chatRoom = chatRoom();
        ChatRoomMember roomMember = ChatRoomMember.createMember(chatRoom, requester);
        ReflectionTestUtils.setField(
                roomMember,
                "joinedAt",
                Instant.parse("2026-09-16T00:00:00Z")
        );
        ChatMessage message = message(
                chatRoom,
                sender,
                100L,
                "다른 사용자의 메시지",
                "2026-09-16T01:00:00Z"
        );

        stubMessageAccess(requester, chatRoom, roomMember);
        when(chatMessageRepository.findByIdAndRoomIdForUpdate(100L, 10L))
                .thenReturn(Optional.of(message));

        assertThatThrownBy(() ->
                chatRoomService.editMessage(10L, 100L, 1L, "수정 내용")
        ).isInstanceOfSatisfying(
                CustomException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_MESSAGE_EDIT_FORBIDDEN)
        );

        assertThat(message.getEditedAt()).isNull();
    }

    @Test
    void editMessageHidesMessageCreatedBeforeCurrentParticipation() {
        Member member = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom chatRoom = chatRoom();
        ChatRoomMember roomMember = ChatRoomMember.createMember(chatRoom, member);
        ReflectionTestUtils.setField(
                roomMember,
                "joinedAt",
                Instant.parse("2026-09-16T02:00:00Z")
        );
        ChatMessage message = message(
                chatRoom,
                member,
                100L,
                "참여 이전 메시지",
                "2026-09-16T01:00:00Z"
        );

        stubMessageAccess(member, chatRoom, roomMember);
        when(chatMessageRepository.findByIdAndRoomIdForUpdate(100L, 10L))
                .thenReturn(Optional.of(message));

        assertThatThrownBy(() ->
                chatRoomService.editMessage(10L, 100L, 1L, "수정 내용")
        ).isInstanceOfSatisfying(
                CustomException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_MESSAGE_NOT_FOUND)
        );

        assertThat(message.getEditedAt()).isNull();
    }

    @Test
    void getMembersReturnsParticipatingMembersWithStatusBasedDisplayNames() {
        Member requester = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom chatRoom = chatRoom();
        ChatRoomMember requesterRoomMember =
                ChatRoomMember.createMember(chatRoom, requester);

        Member activeMember = member(2L, "활성회원", MemberStatus.ACTIVE);
        Member suspendedMember = member(3L, "정지회원", MemberStatus.SUSPENDED);
        Member withdrawnMember = member(4L, "탈퇴회원", MemberStatus.WITHDRAWN);

        ChatRoomMember activeRoomMember =
                ChatRoomMember.createMember(chatRoom, activeMember);
        ChatRoomMember suspendedRoomMember =
                ChatRoomMember.createMember(chatRoom, suspendedMember);
        ChatRoomMember withdrawnRoomMember =
                ChatRoomMember.createMember(chatRoom, withdrawnMember);

        stubMessageAccess(requester, chatRoom, requesterRoomMember);
        when(chatRoomMemberRepository.findAllParticipatingByChatRoomId(10L))
                .thenReturn(List.of(
                        activeRoomMember,
                        suspendedRoomMember,
                        withdrawnRoomMember
                ));

        List<ChatRoomMemberResponse> response =
                chatRoomService.getMembers(10L, 1L);

        assertThat(response)
                .extracting(
                        ChatRoomMemberResponse::memberId,
                        ChatRoomMemberResponse::displayName,
                        ChatRoomMemberResponse::role
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                2L,
                                "활성회원",
                                ChatRoomMemberRole.MEMBER
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                3L,
                                "비활성화된 회원",
                                ChatRoomMemberRole.MEMBER
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                4L,
                                "탈퇴한 유저",
                                ChatRoomMemberRole.MEMBER
                        )
                );

        verify(chatRoomMemberRepository)
                .findAllParticipatingByChatRoomId(10L);
    }

    @Test
    void getMembersRejectsMemberWhoIsNotParticipating() {
        Member requester = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom chatRoom = chatRoom();

        when(chatRoomRepository.findById(10L))
                .thenReturn(Optional.of(chatRoom));
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(requester));
        when(chatRoomMemberRepository.findByChatRoomIdAndMemberId(10L, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatRoomService.getMembers(10L, 1L))
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHAT_ROOM_ACCESS_DENIED)
                );

        verify(chatRoomMemberRepository, never())
                .findAllParticipatingByChatRoomId(any(Long.class));
    }

    private void assertCreationRejected(ErrorCode expectedErrorCode) {
        assertThatThrownBy(() -> chatRoomService.create(1L, "Backend"))
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(expectedErrorCode)
                );

        verify(chatRoomRepository, never()).save(any(ChatRoom.class));
        verify(chatRoomMemberRepository, never())
                .save(any(ChatRoomMember.class));
    }

    private void assertAvailableRoomsRejected(ErrorCode expectedErrorCode) {
        assertThatThrownBy(() -> chatRoomService.getGroupChatRooms(1L))
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(expectedErrorCode)
                );

        verify(chatRoomRepository, never()).findAllGroupChatRoom(
                any(Long.class),
                any(ChatRoomType.class)
        );
    }

    private Member member(
            MemberStatus status,
            Instant emailVerifiedAt
    ) {
        Member member = Member.create(
                null,
                "user@example.com",
                "사용자"
        );
        ReflectionTestUtils.setField(member, "id", 1L);
        ReflectionTestUtils.setField(member, "status", status);
        ReflectionTestUtils.setField(
                member,
                "emailVerifiedAt",
                emailVerifiedAt
        );
        return member;
    }

    private Member member(
            Long id,
            String nickname,
            MemberStatus status
    ) {
        Member member = Member.create(
                null,
                nickname + "@example.com",
                nickname
        );
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "status", status);
        return member;
    }

    private ChatRoom chatRoom() {
        ChatRoom chatRoom = ChatRoom.create("Backend");
        ReflectionTestUtils.setField(chatRoom, "id", 10L);
        return chatRoom;
    }

    private void stubMessageAccess(
            Member member,
            ChatRoom chatRoom,
            ChatRoomMember roomMember
    ) {
        when(chatRoomRepository.findById(10L))
                .thenReturn(Optional.of(chatRoom));
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));
        when(chatRoomMemberRepository.findByChatRoomIdAndMemberId(10L, 1L))
                .thenReturn(Optional.of(roomMember));
    }

    private void stubLockedMessageAccess(
            Member member,
            ChatRoom chatRoom,
            ChatRoomMember roomMember
    ) {
        when(chatRoomRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(chatRoom));
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));
        when(chatRoomMemberRepository.findByChatRoomIdAndMemberId(10L, 1L))
                .thenReturn(Optional.of(roomMember));
    }

    private void stubReadPositionAccess(
            Member member,
            ChatRoom chatRoom,
            ChatRoomMember roomMember
    ) {
        when(chatRoomRepository.findById(10L))
                .thenReturn(Optional.of(chatRoom));
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));
        when(chatRoomMemberRepository
                .findByChatRoomIdAndMemberIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(roomMember));
    }

    private ChatMessage message(
            ChatRoom chatRoom,
            Member sender,
            Long id,
            String content,
            String createdAt
    ) {
        ChatMessage message = ChatMessage.createText(chatRoom, sender, content);
        ReflectionTestUtils.setField(message, "id", id);
        ReflectionTestUtils.setField(message, "createdAt", Instant.parse(createdAt));
        return message;
    }

    private void stubSavedMessage(Instant createdAt) {
        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenAnswer(invocation -> {
                    ChatMessage message = invocation.getArgument(0);
                    ReflectionTestUtils.setField(message, "id", 100L);
                    ReflectionTestUtils.setField(message, "createdAt", createdAt);
                    return message;
                });
    }
}
