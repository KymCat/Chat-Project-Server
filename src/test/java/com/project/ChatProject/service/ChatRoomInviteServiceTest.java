package com.project.ChatProject.service;

import com.project.ChatProject.dto.event.ChatMessageEvent;
import com.project.ChatProject.dto.response.ChatRoomInviteLinkResponse;
import com.project.ChatProject.dto.response.ChatMessageResponse;
import com.project.ChatProject.entity.ChatRoom;
import com.project.ChatProject.entity.ChatRoomMember;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.enums.ChatMessageType;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.invite.ChatRoomInviteLink;
import com.project.ChatProject.invite.ChatRoomInviteLinkRepository;
import com.project.ChatProject.invite.ChatRoomInviteTokenGenerator;
import com.project.ChatProject.invite.InviteLinkExpiration;
import com.project.ChatProject.service.support.ChatRoomParticipationContext;
import com.project.ChatProject.service.support.ParticipationLockMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRoomInviteServiceTest {

    private static final Long ROOM_ID = 10L;
    private static final Long MEMBER_ID = 1L;
    private static final String TOKEN = "invite-token";

    @Mock
    private ChatRoomService chatRoomService;

    @Mock
    private ChatRoomInviteTokenGenerator tokenGenerator;

    @Mock
    private ChatRoomInviteLinkRepository inviteLinkRepository;

    @Mock
    private ChatRoomParticipationService participationService;

    private ChatRoomInviteService inviteService;

    @BeforeEach
    void setUp() {
        inviteService = new ChatRoomInviteService(
                chatRoomService,
                tokenGenerator,
                inviteLinkRepository,
                participationService
        );
    }

    @Test
    void getLinkReturnsActiveLinkForParticipant() {
        ChatRoomInviteLink link = link(TOKEN);
        when(inviteLinkRepository.findByRoomId(ROOM_ID))
                .thenReturn(Optional.of(link));

        ChatRoomInviteLinkResponse result =
                inviteService.getLink(ROOM_ID, MEMBER_ID);

        assertThat(result.roomId()).isEqualTo(link.roomId());
        assertThat(result.token()).isEqualTo(link.token());
        assertThat(result.expiresAt()).isEqualTo(link.expiresAt());
        verify(participationService).requireParticipation(
                ROOM_ID,
                MEMBER_ID,
                ParticipationLockMode.NONE
        );
    }

    @Test
    void getLinkRejectsMissingLink() {
        when(inviteLinkRepository.findByRoomId(ROOM_ID))
                .thenReturn(Optional.empty());

        assertErrorCode(
                () -> inviteService.getLink(ROOM_ID, MEMBER_ID),
                ErrorCode.CHAT_ROOM_INVITE_LINK_NOT_FOUND
        );
    }

    @Test
    void createInviteLinkCreatesAndStoresLinkForOwner() {
        stubOwnerParticipation();
        when(inviteLinkRepository.findByRoomId(ROOM_ID))
                .thenReturn(Optional.empty());
        when(tokenGenerator.generate()).thenReturn(TOKEN);
        Instant before = Instant.now();

        ChatRoomInviteLinkResponse result = inviteService.createInviteLink(
                ROOM_ID,
                MEMBER_ID,
                InviteLinkExpiration.ONE_DAY
        );

        Instant after = Instant.now();
        assertThat(result.roomId()).isEqualTo(ROOM_ID);
        assertThat(result.token()).isEqualTo(TOKEN);
        assertThat(result.expiresAt())
                .isBetween(
                        before.plus(InviteLinkExpiration.ONE_DAY.getDuration()),
                        after.plus(InviteLinkExpiration.ONE_DAY.getDuration())
                );
        ArgumentCaptor<ChatRoomInviteLink> linkCaptor =
                ArgumentCaptor.forClass(ChatRoomInviteLink.class);
        verify(inviteLinkRepository).saveOrReplace(linkCaptor.capture());
        assertThat(linkCaptor.getValue().roomId()).isEqualTo(result.roomId());
        assertThat(linkCaptor.getValue().token()).isEqualTo(result.token());
        assertThat(linkCaptor.getValue().expiresAt())
                .isEqualTo(result.expiresAt());
    }

    @Test
    void createInviteLinkRejectsExistingLink() {
        stubOwnerParticipation();
        when(inviteLinkRepository.findByRoomId(ROOM_ID))
                .thenReturn(Optional.of(link(TOKEN)));

        assertErrorCode(
                () -> inviteService.createInviteLink(
                        ROOM_ID,
                        MEMBER_ID,
                        InviteLinkExpiration.ONE_DAY
                ),
                ErrorCode.CHAT_ROOM_LINK_ALREADY_EXISTS
        );

        verify(tokenGenerator, never()).generate();
        verify(inviteLinkRepository, never()).saveOrReplace(
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void createInviteLinkRejectsNonOwner() {
        when(participationService.requireParticipation(
                ROOM_ID,
                MEMBER_ID,
                ParticipationLockMode.CHAT_ROOM
        )).thenReturn(memberParticipation());

        assertErrorCode(
                () -> inviteService.createInviteLink(
                        ROOM_ID,
                        MEMBER_ID,
                        InviteLinkExpiration.ONE_DAY
                ),
                ErrorCode.CHAT_ROOM_OWNER_REQUIRED
        );

        verify(inviteLinkRepository, never()).findByRoomId(ROOM_ID);
    }

    @Test
    void reissueInviteLinkReplacesExistingLink() {
        stubOwnerParticipation();
        when(inviteLinkRepository.findByRoomId(ROOM_ID))
                .thenReturn(Optional.of(link("old-token")));
        when(tokenGenerator.generate()).thenReturn("new-token");

        ChatRoomInviteLinkResponse result = inviteService.reissueInviteLink(
                ROOM_ID,
                MEMBER_ID,
                InviteLinkExpiration.THREE_DAYS
        );

        assertThat(result.token()).isEqualTo("new-token");
        verify(inviteLinkRepository).saveOrReplace(
                org.mockito.ArgumentMatchers.argThat(
                        link -> link.token().equals("new-token")
                )
        );
    }

    @Test
    void reissueInviteLinkRejectsMissingLink() {
        stubOwnerParticipation();
        when(inviteLinkRepository.findByRoomId(ROOM_ID))
                .thenReturn(Optional.empty());

        assertErrorCode(
                () -> inviteService.reissueInviteLink(
                        ROOM_ID,
                        MEMBER_ID,
                        InviteLinkExpiration.THREE_DAYS
                ),
                ErrorCode.CHAT_ROOM_INVITE_LINK_NOT_FOUND
        );

        verify(tokenGenerator, never()).generate();
    }

    @Test
    void deleteInviteLinkDeletesExistingLink() {
        stubOwnerParticipation();
        when(inviteLinkRepository.findByRoomId(ROOM_ID))
                .thenReturn(Optional.of(link(TOKEN)));

        inviteService.deleteInviteLink(ROOM_ID, MEMBER_ID);

        verify(inviteLinkRepository).deleteByRoomId(ROOM_ID);
    }

    @Test
    void deleteInviteLinkRejectsMissingLink() {
        stubOwnerParticipation();
        when(inviteLinkRepository.findByRoomId(ROOM_ID))
                .thenReturn(Optional.empty());

        assertErrorCode(
                () -> inviteService.deleteInviteLink(ROOM_ID, MEMBER_ID),
                ErrorCode.CHAT_ROOM_INVITE_LINK_NOT_FOUND
        );

        verify(inviteLinkRepository, never()).deleteByRoomId(ROOM_ID);
    }

    @Test
    void joinInviteLinkRevalidatesTokenAfterLockAndJoinsRoom() {
        ChatRoomInviteLink link = link(TOKEN);
        ChatMessageEvent event = joinEvent();
        when(inviteLinkRepository.findByToken(TOKEN))
                .thenReturn(Optional.of(link));
        when(inviteLinkRepository.findByRoomId(ROOM_ID))
                .thenReturn(Optional.of(link));
        when(chatRoomService.join(MEMBER_ID, ROOM_ID))
                .thenReturn(event);

        ChatMessageEvent result =
                inviteService.joinInviteLink(TOKEN, MEMBER_ID);

        assertThat(result).isSameAs(event);
        verify(participationService)
                .requireJoinableChatRoomForUpdate(ROOM_ID);
        verify(chatRoomService).join(MEMBER_ID, ROOM_ID);
    }

    @Test
    void joinInviteLinkRejectsUnknownTokenBeforeLock() {
        when(inviteLinkRepository.findByToken(TOKEN))
                .thenReturn(Optional.empty());

        assertErrorCode(
                () -> inviteService.joinInviteLink(TOKEN, MEMBER_ID),
                ErrorCode.CHAT_ROOM_INVITE_LINK_NOT_FOUND
        );

        verify(participationService, never())
                .requireJoinableChatRoomForUpdate(ROOM_ID);
        verify(chatRoomService, never()).join(MEMBER_ID, ROOM_ID);
    }

    @Test
    void joinInviteLinkRejectsTokenReissuedWhileWaitingForLock() {
        when(inviteLinkRepository.findByToken(TOKEN))
                .thenReturn(Optional.of(link(TOKEN)));
        when(inviteLinkRepository.findByRoomId(ROOM_ID))
                .thenReturn(Optional.of(link("new-token")));

        assertErrorCode(
                () -> inviteService.joinInviteLink(TOKEN, MEMBER_ID),
                ErrorCode.CHAT_ROOM_INVITE_LINK_NOT_FOUND
        );

        verify(participationService)
                .requireJoinableChatRoomForUpdate(ROOM_ID);
        verify(chatRoomService, never()).join(MEMBER_ID, ROOM_ID);
    }

    private void stubOwnerParticipation() {
        when(participationService.requireParticipation(
                ROOM_ID,
                MEMBER_ID,
                ParticipationLockMode.CHAT_ROOM
        )).thenReturn(ownerParticipation());
    }

    private ChatRoomParticipationContext ownerParticipation() {
        ChatRoom chatRoom = ChatRoom.create("Backend");
        Member member = Member.create(null, "owner@example.com", "방장");
        ChatRoomMember roomMember = ChatRoomMember.create(chatRoom, member);
        return new ChatRoomParticipationContext(
                chatRoom,
                member,
                roomMember
        );
    }

    private ChatRoomParticipationContext memberParticipation() {
        ChatRoom chatRoom = ChatRoom.create("Backend");
        Member member = Member.create(null, "member@example.com", "멤버");
        ChatRoomMember roomMember =
                ChatRoomMember.createMember(chatRoom, member);
        return new ChatRoomParticipationContext(
                chatRoom,
                member,
                roomMember
        );
    }

    private ChatRoomInviteLink link(String token) {
        return ChatRoomInviteLink.of(
                ROOM_ID,
                token,
                Instant.parse("2026-10-16T00:00:00Z")
        );
    }

    private ChatMessageEvent joinEvent() {
        ChatMessageResponse message = new ChatMessageResponse(
                100L,
                ROOM_ID,
                null,
                null,
                ChatMessageType.SYSTEM,
                "사용자님이 입장하였습니다.",
                null,
                Instant.parse("2026-10-15T00:00:00Z"),
                null,
                false
        );
        return ChatMessageEvent.created(message);
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
