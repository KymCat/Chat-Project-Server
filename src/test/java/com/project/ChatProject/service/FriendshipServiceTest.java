package com.project.ChatProject.service;

import com.project.ChatProject.dto.response.CursorPageResponse;
import com.project.ChatProject.dto.response.FriendRequestResponse;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
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
    private static final Long REQUEST_ID = 100L;

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

    @Test
    void requestedListReturnsSentRequestsWithNextCursor() {
        when(memberRepository.findById(REQUESTER_ID))
                .thenReturn(Optional.of(requester));
        Member secondReceiver = member(
                3L,
                "second@example.com",
                "두 번째 수신자",
                MemberStatus.ACTIVE
        );
        Member thirdReceiver = member(
                4L,
                "third@example.com",
                "세 번째 수신자",
                MemberStatus.ACTIVE
        );
        List<Friendship> friendships = List.of(
                friendship(30L, requester, receiver),
                friendship(20L, requester, secondReceiver),
                friendship(10L, requester, thirdReceiver)
        );
        when(friendshipRepository.findSentRequests(
                REQUESTER_ID,
                FriendshipStatus.PENDING,
                PageRequest.of(0, 3)
        )).thenReturn(friendships);

        CursorPageResponse<FriendRequestResponse> response =
                friendshipService.requestedList(
                        REQUESTER_ID,
                        null,
                        2
                );

        assertThat(response.content()).hasSize(2);
        assertThat(response.nextCursor()).isEqualTo(20L);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.content().get(0).requestId()).isEqualTo(30L);
        assertThat(response.content().get(0).targetMemberId())
                .isEqualTo(RECEIVER_ID);
        assertThat(response.content().get(0).targetNickname())
                .isEqualTo("수신자");
        assertThat(response.content().get(1).targetMemberId())
                .isEqualTo(3L);

        verify(friendshipRepository).findSentRequests(
                REQUESTER_ID,
                FriendshipStatus.PENDING,
                PageRequest.of(0, 3)
        );
        verify(friendshipRepository, never()).findSentRequestsBefore(
                any(Long.class),
                any(Long.class),
                any(FriendshipStatus.class),
                any(PageRequest.class)
        );
    }

    @Test
    void requestedListUsesCursorAndReturnsNoNextPage() {
        when(memberRepository.findById(REQUESTER_ID))
                .thenReturn(Optional.of(requester));
        Friendship friendship = friendship(
                10L,
                requester,
                receiver
        );
        when(friendshipRepository.findSentRequestsBefore(
                20L,
                REQUESTER_ID,
                FriendshipStatus.PENDING,
                PageRequest.of(0, 3)
        )).thenReturn(List.of(friendship));

        CursorPageResponse<FriendRequestResponse> response =
                friendshipService.requestedList(
                        REQUESTER_ID,
                        20L,
                        2
                );

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).requestId()).isEqualTo(10L);
        assertThat(response.nextCursor()).isNull();
        assertThat(response.hasNext()).isFalse();

        verify(friendshipRepository).findSentRequestsBefore(
                20L,
                REQUESTER_ID,
                FriendshipStatus.PENDING,
                PageRequest.of(0, 3)
        );
        verify(friendshipRepository, never()).findSentRequests(
                any(Long.class),
                any(FriendshipStatus.class),
                any(PageRequest.class)
        );
    }

    @Test
    void receivedListReturnsRequestersAsTargetMembers() {
        when(memberRepository.findById(REQUESTER_ID))
                .thenReturn(Optional.of(requester));
        Member secondRequester = member(
                3L,
                "second@example.com",
                "두 번째 요청자",
                MemberStatus.ACTIVE
        );
        List<Friendship> friendships = List.of(
                friendship(30L, receiver, requester),
                friendship(20L, secondRequester, requester)
        );
        when(friendshipRepository.findReceivedRequests(
                REQUESTER_ID,
                FriendshipStatus.PENDING,
                PageRequest.of(0, 3)
        )).thenReturn(friendships);

        CursorPageResponse<FriendRequestResponse> response =
                friendshipService.receivedList(
                        REQUESTER_ID,
                        null,
                        2
                );

        assertThat(response.content()).hasSize(2);
        assertThat(response.nextCursor()).isNull();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.content().get(0).targetMemberId())
                .isEqualTo(RECEIVER_ID);
        assertThat(response.content().get(0).targetNickname())
                .isEqualTo("수신자");
        assertThat(response.content().get(1).targetMemberId())
                .isEqualTo(3L);

        verify(friendshipRepository).findReceivedRequests(
                REQUESTER_ID,
                FriendshipStatus.PENDING,
                PageRequest.of(0, 3)
        );
        verify(friendshipRepository, never()).findReceivedRequestsBefore(
                any(Long.class),
                any(Long.class),
                any(FriendshipStatus.class),
                any(PageRequest.class)
        );
    }

    @Test
    void receivedListUsesCursorAndReturnsNextCursor() {
        when(memberRepository.findById(REQUESTER_ID))
                .thenReturn(Optional.of(requester));
        Member secondRequester = member(
                3L,
                "second@example.com",
                "두 번째 요청자",
                MemberStatus.ACTIVE
        );
        Member thirdRequester = member(
                4L,
                "third@example.com",
                "세 번째 요청자",
                MemberStatus.ACTIVE
        );
        List<Friendship> friendships = List.of(
                friendship(30L, receiver, requester),
                friendship(20L, secondRequester, requester),
                friendship(10L, thirdRequester, requester)
        );
        when(friendshipRepository.findReceivedRequestsBefore(
                40L,
                REQUESTER_ID,
                FriendshipStatus.PENDING,
                PageRequest.of(0, 3)
        )).thenReturn(friendships);

        CursorPageResponse<FriendRequestResponse> response =
                friendshipService.receivedList(
                        REQUESTER_ID,
                        40L,
                        2
                );

        assertThat(response.content()).hasSize(2);
        assertThat(response.nextCursor()).isEqualTo(20L);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.content().get(0).targetMemberId())
                .isEqualTo(RECEIVER_ID);
        assertThat(response.content().get(1).targetMemberId())
                .isEqualTo(3L);

        verify(friendshipRepository).findReceivedRequestsBefore(
                40L,
                REQUESTER_ID,
                FriendshipStatus.PENDING,
                PageRequest.of(0, 3)
        );
        verify(friendshipRepository, never()).findReceivedRequests(
                any(Long.class),
                any(FriendshipStatus.class),
                any(PageRequest.class)
        );
    }

    @Test
    void acceptRequestAcceptsPendingReceivedRequest() {
        stubMembers();
        Friendship request = friendship(
                REQUEST_ID,
                requester,
                receiver
        );
        when(friendshipRepository.findReceivedRequestByIdForUpdate(
                REQUEST_ID,
                RECEIVER_ID
        )).thenReturn(Optional.of(request));

        friendshipService.acceptRequest(
                REQUEST_ID,
                RECEIVER_ID
        );

        assertThat(request.getStatus())
                .isEqualTo(FriendshipStatus.ACCEPTED);
        assertThat(request.getAcceptedAt()).isNotNull();
        verify(friendshipRepository).findReceivedRequestByIdForUpdate(
                REQUEST_ID,
                RECEIVER_ID
        );
    }

    @Test
    void acceptRequestRejectsUnknownOrUnauthorizedRequest() {
        when(memberRepository.findById(RECEIVER_ID))
                .thenReturn(Optional.of(receiver));
        when(friendshipRepository.findReceivedRequestByIdForUpdate(
                REQUEST_ID,
                RECEIVER_ID
        )).thenReturn(Optional.empty());

        assertAcceptRequestRejected(
                ErrorCode.FRIEND_REQUEST_NOT_FOUND
        );

        verify(memberRepository, never()).findById(REQUESTER_ID);
    }

    @Test
    void acceptRequestRejectsAlreadyAcceptedRequest() {
        when(memberRepository.findById(RECEIVER_ID))
                .thenReturn(Optional.of(receiver));
        Friendship request = friendship(
                REQUEST_ID,
                requester,
                receiver
        );
        request.acceptRequest();
        when(friendshipRepository.findReceivedRequestByIdForUpdate(
                REQUEST_ID,
                RECEIVER_ID
        )).thenReturn(Optional.of(request));

        assertAcceptRequestRejected(
                ErrorCode.FRIEND_REQUEST_ALREADY_ACCEPTED
        );

        verify(memberRepository, never()).findById(REQUESTER_ID);
    }

    @Test
    void acceptRequestRejectsSuspendedRequester() {
        ReflectionTestUtils.setField(
                requester,
                "status",
                MemberStatus.SUSPENDED
        );
        stubMembers();
        Friendship request = friendship(
                REQUEST_ID,
                requester,
                receiver
        );
        when(friendshipRepository.findReceivedRequestByIdForUpdate(
                REQUEST_ID,
                RECEIVER_ID
        )).thenReturn(Optional.of(request));

        assertAcceptRequestRejected(ErrorCode.MEMBER_BLOCKED);

        assertThat(request.getStatus())
                .isEqualTo(FriendshipStatus.PENDING);
        assertThat(request.getAcceptedAt()).isNull();
    }

    @Test
    void acceptRequestRejectsWithdrawnRequester() {
        ReflectionTestUtils.setField(
                requester,
                "status",
                MemberStatus.WITHDRAWN
        );
        stubMembers();
        Friendship request = friendship(
                REQUEST_ID,
                requester,
                receiver
        );
        when(friendshipRepository.findReceivedRequestByIdForUpdate(
                REQUEST_ID,
                RECEIVER_ID
        )).thenReturn(Optional.of(request));

        assertAcceptRequestRejected(ErrorCode.MEMBER_WITHDRAWN);

        assertThat(request.getStatus())
                .isEqualTo(FriendshipStatus.PENDING);
        assertThat(request.getAcceptedAt()).isNull();
    }

    @Test
    void rejectRequestDeletesPendingReceivedRequest() {
        when(memberRepository.findById(RECEIVER_ID))
                .thenReturn(Optional.of(receiver));
        Friendship request = friendship(
                REQUEST_ID,
                requester,
                receiver
        );
        when(friendshipRepository.findReceivedRequestByIdForUpdate(
                REQUEST_ID,
                RECEIVER_ID
        )).thenReturn(Optional.of(request));

        friendshipService.rejectRequest(
                REQUEST_ID,
                RECEIVER_ID
        );

        verify(friendshipRepository).findReceivedRequestByIdForUpdate(
                REQUEST_ID,
                RECEIVER_ID
        );
        verify(friendshipRepository).delete(request);
    }

    @Test
    void rejectRequestIgnoresUnknownOrUnauthorizedRequest() {
        when(memberRepository.findById(RECEIVER_ID))
                .thenReturn(Optional.of(receiver));
        when(friendshipRepository.findReceivedRequestByIdForUpdate(
                REQUEST_ID,
                RECEIVER_ID
        )).thenReturn(Optional.empty());

        friendshipService.rejectRequest(
                REQUEST_ID,
                RECEIVER_ID
        );

        verify(friendshipRepository, never())
                .delete(any(Friendship.class));
    }

    @Test
    void rejectRequestRejectsAcceptedFriendship() {
        when(memberRepository.findById(RECEIVER_ID))
                .thenReturn(Optional.of(receiver));
        Friendship request = friendship(
                REQUEST_ID,
                requester,
                receiver
        );
        request.acceptRequest();
        when(friendshipRepository.findReceivedRequestByIdForUpdate(
                REQUEST_ID,
                RECEIVER_ID
        )).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> friendshipService.rejectRequest(
                REQUEST_ID,
                RECEIVER_ID
        )).isInstanceOfSatisfying(
                CustomException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(
                                ErrorCode.FRIEND_REQUEST_ALREADY_ACCEPTED
                        )
        );

        verify(friendshipRepository, never()).delete(request);
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

    private void assertAcceptRequestRejected(
            ErrorCode expectedErrorCode
    ) {
        assertThatThrownBy(() -> friendshipService.acceptRequest(
                REQUEST_ID,
                RECEIVER_ID
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

    private Friendship friendship(
            Long id,
            Member requester,
            Member receiver
    ) {
        Friendship friendship = Friendship.create(requester, receiver);
        ReflectionTestUtils.setField(friendship, "id", id);
        ReflectionTestUtils.setField(
                friendship,
                "createdAt",
                Instant.parse("2026-10-29T00:00:00Z")
        );
        return friendship;
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
