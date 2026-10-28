package com.project.ChatProject.service;

import com.project.ChatProject.entity.Friendship;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.enums.FriendshipStatus;
import com.project.ChatProject.entity.enums.MemberStatus;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.repository.FriendshipRepository;
import com.project.ChatProject.repository.MemberRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendshipServiceTest {

    private static final Long REQUESTER_ID = 1L;
    private static final Long RECEIVER_ID = 2L;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    private FriendshipService friendshipService;
    private Member requester;
    private Member receiver;

    @BeforeEach
    void setUp() {
        friendshipService = new FriendshipService(
                memberRepository,
                friendshipRepository
        );

        requester = member(
                REQUESTER_ID,
                "requester@example.com",
                "요청자",
                MemberStatus.ACTIVE
        );
        receiver = member(
                RECEIVER_ID,
                "receiver@example.com",
                "수신자",
                MemberStatus.ACTIVE
        );
    }

    @Test
    void requestFriendStoresPendingFriendship() {
        stubMembers();

        friendshipService.requestFriend(
                REQUESTER_ID,
                RECEIVER_ID
        );

        ArgumentCaptor<Friendship> captor =
                ArgumentCaptor.forClass(Friendship.class);
        verify(friendshipRepository).saveAndFlush(captor.capture());

        Friendship savedFriendship = captor.getValue();
        assertThat(savedFriendship.getRequester()).isSameAs(requester);
        assertThat(savedFriendship.getReceiver()).isSameAs(receiver);
        assertThat(savedFriendship.getStatus())
                .isEqualTo(FriendshipStatus.PENDING);
        assertThat(savedFriendship.getAcceptedAt()).isNull();

        verify(friendshipRepository).existsBetweenMembersAndStatus(
                REQUESTER_ID,
                RECEIVER_ID,
                FriendshipStatus.ACCEPTED
        );
        verify(friendshipRepository).existsBetweenMembersAndStatus(
                REQUESTER_ID,
                RECEIVER_ID,
                FriendshipStatus.PENDING
        );
    }

    @Test
    void requestFriendRejectsSelfRequest() {
        assertRequestRejected(
                REQUESTER_ID,
                REQUESTER_ID,
                ErrorCode.SELF_FRIEND_REQUEST_NOT_ALLOWED
        );

        verifyNoInteractions(
                memberRepository,
                friendshipRepository
        );
    }

    @Test
    void requestFriendRejectsUnknownRequester() {
        when(memberRepository.findById(REQUESTER_ID))
                .thenReturn(Optional.empty());

        assertRequestRejected(ErrorCode.MEMBER_NOT_FOUND);

        verify(memberRepository, never()).findById(RECEIVER_ID);
        verifyNoInteractions(friendshipRepository);
    }

    @Test
    void requestFriendRejectsUnknownReceiver() {
        when(memberRepository.findById(REQUESTER_ID))
                .thenReturn(Optional.of(requester));
        when(memberRepository.findById(RECEIVER_ID))
                .thenReturn(Optional.empty());

        assertRequestRejected(ErrorCode.MEMBER_NOT_FOUND);

        verifyNoInteractions(friendshipRepository);
    }

    @Test
    void requestFriendRejectsSuspendedRequester() {
        ReflectionTestUtils.setField(
                requester,
                "status",
                MemberStatus.SUSPENDED
        );
        when(memberRepository.findById(REQUESTER_ID))
                .thenReturn(Optional.of(requester));

        assertRequestRejected(ErrorCode.MEMBER_BLOCKED);

        verify(memberRepository, never()).findById(RECEIVER_ID);
        verifyNoInteractions(friendshipRepository);
    }

    @Test
    void requestFriendRejectsWithdrawnReceiver() {
        ReflectionTestUtils.setField(
                receiver,
                "status",
                MemberStatus.WITHDRAWN
        );
        when(memberRepository.findById(REQUESTER_ID))
                .thenReturn(Optional.of(requester));
        when(memberRepository.findById(RECEIVER_ID))
                .thenReturn(Optional.of(receiver));

        assertRequestRejected(ErrorCode.MEMBER_WITHDRAWN);

        verifyNoInteractions(friendshipRepository);
    }

    @Test
    void requestFriendRejectsExistingAcceptedFriendship() {
        stubMembers();
        when(friendshipRepository.existsBetweenMembersAndStatus(
                REQUESTER_ID,
                RECEIVER_ID,
                FriendshipStatus.ACCEPTED
        )).thenReturn(true);

        assertRequestRejected(ErrorCode.ALREADY_FRIENDS);

        verify(friendshipRepository, never())
                .existsBetweenMembersAndStatus(
                        REQUESTER_ID,
                        RECEIVER_ID,
                        FriendshipStatus.PENDING
                );
        verify(friendshipRepository, never())
                .saveAndFlush(any(Friendship.class));
    }

    @Test
    void requestFriendRejectsExistingPendingRequest() {
        stubMembers();
        when(friendshipRepository.existsBetweenMembersAndStatus(
                REQUESTER_ID,
                RECEIVER_ID,
                FriendshipStatus.ACCEPTED
        )).thenReturn(false);
        when(friendshipRepository.existsBetweenMembersAndStatus(
                REQUESTER_ID,
                RECEIVER_ID,
                FriendshipStatus.PENDING
        )).thenReturn(true);

        assertRequestRejected(ErrorCode.FRIEND_REQUEST_EXISTS);

        verify(friendshipRepository, never())
                .saveAndFlush(any(Friendship.class));
    }

    @Test
    void requestFriendConvertsMemberPairUniqueViolation() {
        stubMembers();
        DataIntegrityViolationException databaseFailure =
                databaseFailure("uq_friendships_member_pair");
        when(friendshipRepository.saveAndFlush(any(Friendship.class)))
                .thenThrow(databaseFailure);

        assertRequestRejected(
                ErrorCode.FRIEND_RELATION_ALREADY_EXISTS
        );
    }

    @Test
    void requestFriendRethrowsOtherIntegrityViolation() {
        stubMembers();
        DataIntegrityViolationException databaseFailure =
                databaseFailure("fk_friendships_requester_id");
        when(friendshipRepository.saveAndFlush(any(Friendship.class)))
                .thenThrow(databaseFailure);

        assertThatThrownBy(() -> friendshipService.requestFriend(
                REQUESTER_ID,
                RECEIVER_ID
        )).isSameAs(databaseFailure);
    }

    private void stubMembers() {
        when(memberRepository.findById(REQUESTER_ID))
                .thenReturn(Optional.of(requester));
        when(memberRepository.findById(RECEIVER_ID))
                .thenReturn(Optional.of(receiver));
    }

    private void assertRequestRejected(ErrorCode expectedErrorCode) {
        assertRequestRejected(
                REQUESTER_ID,
                RECEIVER_ID,
                expectedErrorCode
        );
    }

    private void assertRequestRejected(
            Long requesterId,
            Long receiverId,
            ErrorCode expectedErrorCode
    ) {
        assertThatThrownBy(() -> friendshipService.requestFriend(
                requesterId,
                receiverId
        )).isInstanceOfSatisfying(
                CustomException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(expectedErrorCode)
        );
    }

    private Member member(
            Long id,
            String email,
            String nickname,
            MemberStatus status
    ) {
        Member member = Member.create(email, nickname);
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "status", status);
        return member;
    }

    private DataIntegrityViolationException databaseFailure(
            String constraintName
    ) {
        ConstraintViolationException constraintFailure =
                new ConstraintViolationException(
                        "DB 제약조건 위반",
                        new SQLException(),
                        constraintName
                );

        return new DataIntegrityViolationException(
                "DB 저장 실패",
                constraintFailure
        );
    }
}
