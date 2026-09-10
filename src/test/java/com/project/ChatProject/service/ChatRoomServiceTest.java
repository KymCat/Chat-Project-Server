package com.project.ChatProject.service;

import com.project.ChatProject.dto.response.ChatRoomCreateResponse;
import com.project.ChatProject.entity.ChatRoom;
import com.project.ChatProject.entity.ChatRoomMember;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.enums.ChatRoomMemberRole;
import com.project.ChatProject.entity.enums.ChatRoomType;
import com.project.ChatProject.entity.enums.MemberStatus;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
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

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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

    private Member member(
            MemberStatus status,
            Instant emailVerifiedAt
    ) {
        Member member = Member.create(
                null,
                "user@example.com",
                "사용자"
        );
        ReflectionTestUtils.setField(member, "status", status);
        ReflectionTestUtils.setField(
                member,
                "emailVerifiedAt",
                emailVerifiedAt
        );
        return member;
    }
}
