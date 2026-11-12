package com.project.ChatProject.service;

import com.project.ChatProject.dto.event.MemberEvent;
import com.project.ChatProject.dto.event.MemberEventType;
import com.project.ChatProject.entity.Friendship;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.MemberBlock;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.repository.FriendshipRepository;
import com.project.ChatProject.repository.MemberBlockRepository;
import com.project.ChatProject.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberBlockServiceTest {

    private static final Long BLOCKER_ID = 1L;
    private static final Long BLOCKED_ID = 2L;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberBlockRepository memberBlockRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    @InjectMocks
    private MemberBlockService memberBlockService;

    @Test
    void blockStoresBlockDeletesFriendshipAndReturnsHiddenMemberEvent() {
        Member blocker = org.mockito.Mockito.mock(Member.class);
        Member blocked = org.mockito.Mockito.mock(Member.class);
        Friendship friendship = org.mockito.Mockito.mock(Friendship.class);
        when(memberRepository.findById(BLOCKER_ID)).thenReturn(Optional.of(blocker));
        when(memberRepository.findById(BLOCKED_ID)).thenReturn(Optional.of(blocked));
        when(friendshipRepository.findBetweenMembers(BLOCKER_ID, BLOCKED_ID))
                .thenReturn(Optional.of(friendship));

        MemberEvent event = memberBlockService.block(BLOCKER_ID, BLOCKED_ID);

        assertThat(event.type()).isEqualTo(MemberEventType.BLOCKED);
        assertThat(event.memberId()).isEqualTo(BLOCKER_ID);

        ArgumentCaptor<MemberBlock> blockCaptor =
                ArgumentCaptor.forClass(MemberBlock.class);
        verify(memberBlockRepository).saveAndFlush(blockCaptor.capture());
        assertThat(blockCaptor.getValue().getBlocker()).isSameAs(blocker);
        assertThat(blockCaptor.getValue().getBlocked()).isSameAs(blocked);
        verify(friendshipRepository).delete(friendship);
    }

    @Test
    void blockRejectsExistingBlock() {
        when(memberRepository.findById(BLOCKER_ID))
                .thenReturn(Optional.of(org.mockito.Mockito.mock(Member.class)));
        when(memberRepository.findById(BLOCKED_ID))
                .thenReturn(Optional.of(org.mockito.Mockito.mock(Member.class)));
        when(memberBlockRepository.existsByBlockerIdAndBlockedId(
                BLOCKER_ID,
                BLOCKED_ID
        )).thenReturn(true);

        assertThatThrownBy(() -> memberBlockService.block(BLOCKER_ID, BLOCKED_ID))
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEMBER_BLOCK_ALREADY_EXISTS)
                );

        verify(memberBlockRepository, never()).saveAndFlush(any(MemberBlock.class));
        verifyNoInteractions(friendshipRepository);
    }

    @Test
    void blockRejectsSelfBlockBeforeRepositoryAccess() {
        assertThatThrownBy(() -> memberBlockService.block(BLOCKER_ID, BLOCKER_ID))
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.SELF_BLOCK_NOT_ALLOWED)
                );

        verifyNoInteractions(
                memberRepository,
                memberBlockRepository,
                friendshipRepository
        );
    }

    @Test
    void unblockDeletesExistingBlock() {
        MemberBlock memberBlock = org.mockito.Mockito.mock(MemberBlock.class);
        when(memberRepository.findById(BLOCKER_ID))
                .thenReturn(Optional.of(org.mockito.Mockito.mock(Member.class)));
        when(memberBlockRepository.findByBlockerIdAndBlockedId(
                BLOCKER_ID,
                BLOCKED_ID
        )).thenReturn(Optional.of(memberBlock));

        memberBlockService.unblock(BLOCKER_ID, BLOCKED_ID);

        verify(memberBlockRepository).delete(memberBlock);
    }
}
