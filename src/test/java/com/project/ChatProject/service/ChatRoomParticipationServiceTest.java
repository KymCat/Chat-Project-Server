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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRoomParticipationServiceTest {

    private static final Long ROOM_ID = 10L;
    private static final Long MEMBER_ID = 1L;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    private ChatRoomParticipationService participationService;

    @BeforeEach
    void setUp() {
        participationService = new ChatRoomParticipationService(
                memberRepository,
                chatRoomMemberRepository,
                chatRoomRepository
        );
    }

    @Test
    void requireActiveMemberReturnsValidatedMember() {
        Member member = member(MemberStatus.ACTIVE, Instant.now());
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));

        Member result = participationService.requireActiveMember(MEMBER_ID);

        assertThat(result).isSameAs(member);
    }

    @Test
    void requireActiveMemberRejectsUnknownMember() {
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.empty());

        assertErrorCode(
                () -> participationService.requireActiveMember(MEMBER_ID),
                ErrorCode.MEMBER_NOT_FOUND
        );
    }

    @Test
    void requireActiveMemberRejectsSuspendedMember() {
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(
                        member(MemberStatus.SUSPENDED, Instant.now())
                ));

        assertErrorCode(
                () -> participationService.requireActiveMember(MEMBER_ID),
                ErrorCode.MEMBER_BLOCKED
        );
    }

    @Test
    void requireActiveMemberRejectsWithdrawnMember() {
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(
                        member(MemberStatus.WITHDRAWN, Instant.now())
                ));

        assertErrorCode(
                () -> participationService.requireActiveMember(MEMBER_ID),
                ErrorCode.MEMBER_WITHDRAWN
        );
    }

    @Test
    void requireActiveMemberRejectsUnverifiedMember() {
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(
                        member(MemberStatus.ACTIVE, null)
                ));

        assertErrorCode(
                () -> participationService.requireActiveMember(MEMBER_ID),
                ErrorCode.EMAIL_VERIFICATION_REQUIRED
        );
    }

    @Test
    void requireJoinableChatRoomReturnsGroupRoom() {
        ChatRoom chatRoom = ChatRoom.create("Backend");
        when(chatRoomRepository.findById(ROOM_ID))
                .thenReturn(Optional.of(chatRoom));

        ChatRoom result =
                participationService.requireJoinableChatRoom(ROOM_ID);

        assertThat(result).isSameAs(chatRoom);
    }

    @Test
    void requireJoinableChatRoomRejectsUnknownRoom() {
        when(chatRoomRepository.findById(ROOM_ID))
                .thenReturn(Optional.empty());

        assertErrorCode(
                () -> participationService.requireJoinableChatRoom(ROOM_ID),
                ErrorCode.CHAT_ROOM_NOT_FOUND
        );
    }

    @Test
    void requireJoinableChatRoomRejectsDeletedRoom() {
        ChatRoom chatRoom = ChatRoom.create("Backend");
        chatRoom.delete();
        when(chatRoomRepository.findById(ROOM_ID))
                .thenReturn(Optional.of(chatRoom));

        assertErrorCode(
                () -> participationService.requireJoinableChatRoom(ROOM_ID),
                ErrorCode.CHAT_ROOM_DELETED
        );
    }

    @Test
    void requireJoinableChatRoomRejectsDirectRoom() {
        ChatRoom chatRoom = ChatRoom.create("Backend");
        ReflectionTestUtils.setField(
                chatRoom,
                "type",
                ChatRoomType.DIRECT
        );
        when(chatRoomRepository.findById(ROOM_ID))
                .thenReturn(Optional.of(chatRoom));

        assertErrorCode(
                () -> participationService.requireJoinableChatRoom(ROOM_ID),
                ErrorCode.INVALID_CHAT_ROOM_TYPE
        );
    }

    @Test
    void requireParticipationWithoutLockReturnsValidatedContext() {
        Member member = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom chatRoom = ChatRoom.create("Backend");
        ChatRoomMember chatRoomMember =
                ChatRoomMember.createMember(chatRoom, member);
        stubActiveParticipation(chatRoom, member, chatRoomMember);

        ChatRoomParticipationContext context =
                participationService.requireParticipation(
                        ROOM_ID,
                        MEMBER_ID,
                        ParticipationLockMode.NONE
                );

        assertThat(context.chatRoom()).isSameAs(chatRoom);
        assertThat(context.member()).isSameAs(member);
        assertThat(context.chatRoomMember()).isSameAs(chatRoomMember);
        verify(chatRoomRepository, never()).findByIdForUpdate(ROOM_ID);
        verify(chatRoomMemberRepository, never())
                .findByChatRoomIdAndMemberIdForUpdate(ROOM_ID, MEMBER_ID);
    }

    @Test
    void requireParticipationWithChatRoomLockUsesLockedRoomLookup() {
        Member member = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom chatRoom = ChatRoom.create("Backend");
        ChatRoomMember chatRoomMember =
                ChatRoomMember.createMember(chatRoom, member);
        when(chatRoomRepository.findByIdForUpdate(ROOM_ID))
                .thenReturn(Optional.of(chatRoom));
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));
        when(chatRoomMemberRepository.findByChatRoomIdAndMemberId(
                ROOM_ID,
                MEMBER_ID
        )).thenReturn(Optional.of(chatRoomMember));

        ChatRoomParticipationContext context =
                participationService.requireParticipation(
                        ROOM_ID,
                        MEMBER_ID,
                        ParticipationLockMode.CHAT_ROOM
                );

        assertThat(context.chatRoom()).isSameAs(chatRoom);
        verify(chatRoomRepository).findByIdForUpdate(ROOM_ID);
        verify(chatRoomRepository, never()).findById(ROOM_ID);
        verify(chatRoomMemberRepository, never())
                .findByChatRoomIdAndMemberIdForUpdate(ROOM_ID, MEMBER_ID);
    }

    @Test
    void requireParticipationWithMemberLockUsesLockedMemberLookup() {
        Member member = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom chatRoom = ChatRoom.create("Backend");
        ChatRoomMember chatRoomMember =
                ChatRoomMember.createMember(chatRoom, member);
        when(chatRoomRepository.findById(ROOM_ID))
                .thenReturn(Optional.of(chatRoom));
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));
        when(chatRoomMemberRepository
                .findByChatRoomIdAndMemberIdForUpdate(ROOM_ID, MEMBER_ID))
                .thenReturn(Optional.of(chatRoomMember));

        ChatRoomParticipationContext context =
                participationService.requireParticipation(
                        ROOM_ID,
                        MEMBER_ID,
                        ParticipationLockMode.CHAT_ROOM_MEMBER
                );

        assertThat(context.chatRoomMember()).isSameAs(chatRoomMember);
        verify(chatRoomRepository).findById(ROOM_ID);
        verify(chatRoomRepository, never()).findByIdForUpdate(ROOM_ID);
        verify(chatRoomMemberRepository)
                .findByChatRoomIdAndMemberIdForUpdate(ROOM_ID, MEMBER_ID);
        verify(chatRoomMemberRepository, never())
                .findByChatRoomIdAndMemberId(ROOM_ID, MEMBER_ID);
    }

    @Test
    void requireParticipationRejectsMissingMembership() {
        Member member = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom chatRoom = ChatRoom.create("Backend");
        when(chatRoomRepository.findById(ROOM_ID))
                .thenReturn(Optional.of(chatRoom));
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));
        when(chatRoomMemberRepository.findByChatRoomIdAndMemberId(
                ROOM_ID,
                MEMBER_ID
        )).thenReturn(Optional.empty());

        assertErrorCode(
                () -> participationService.requireParticipation(
                        ROOM_ID,
                        MEMBER_ID,
                        ParticipationLockMode.NONE
                ),
                ErrorCode.CHAT_ROOM_ACCESS_DENIED
        );
    }

    @Test
    void requireParticipationRejectsFormerMember() {
        Member member = member(MemberStatus.ACTIVE, Instant.now());
        ChatRoom chatRoom = ChatRoom.create("Backend");
        ChatRoomMember chatRoomMember =
                ChatRoomMember.createMember(chatRoom, member);
        chatRoomMember.leave();
        stubActiveParticipation(chatRoom, member, chatRoomMember);

        assertErrorCode(
                () -> participationService.requireParticipation(
                        ROOM_ID,
                        MEMBER_ID,
                        ParticipationLockMode.NONE
                ),
                ErrorCode.CHAT_ROOM_ACCESS_DENIED
        );
    }

    private void stubActiveParticipation(
            ChatRoom chatRoom,
            Member member,
            ChatRoomMember chatRoomMember
    ) {
        when(chatRoomRepository.findById(ROOM_ID))
                .thenReturn(Optional.of(chatRoom));
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));
        when(chatRoomMemberRepository.findByChatRoomIdAndMemberId(
                ROOM_ID,
                MEMBER_ID
        )).thenReturn(Optional.of(chatRoomMember));
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
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        ReflectionTestUtils.setField(member, "status", status);
        ReflectionTestUtils.setField(
                member,
                "emailVerifiedAt",
                emailVerifiedAt
        );
        return member;
    }

    private void assertErrorCode(
            ThrowingCallable callable,
            ErrorCode expectedErrorCode
    ) {
        assertThatThrownBy(callable::call)
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(expectedErrorCode)
                );
    }

    @FunctionalInterface
    private interface ThrowingCallable {
        void call();
    }
}
