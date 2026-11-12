package com.project.ChatProject.service;

import com.project.ChatProject.dto.event.MemberEvent;
import com.project.ChatProject.dto.response.BlockedMemberResponse;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.MemberBlock;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.repository.FriendshipRepository;
import com.project.ChatProject.repository.MemberBlockRepository;
import com.project.ChatProject.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberBlockService {

    private final MemberRepository memberRepository;
    private final MemberBlockRepository memberBlockRepository;
    private final FriendshipRepository friendshipRepository;

    @Transactional
    public MemberEvent block(
            Long blockerId,
            Long blockedId
    ) {
        validateDifferentMembers(blockerId, blockedId);

        Member blocker = findMember(blockerId);
        Member blocked = findMember(blockedId);

        if (memberBlockRepository.existsByBlockerIdAndBlockedId(
                blockerId,
                blockedId
        )) {
            throw new CustomException(
                    ErrorCode.MEMBER_BLOCK_ALREADY_EXISTS
            );
        }

        try {
            memberBlockRepository.saveAndFlush(
                    MemberBlock.create(blocker, blocked)
            );
        } catch (DataIntegrityViolationException exception) {
            if (isMemberBlockConflict(exception)) {
                throw new CustomException(
                        ErrorCode.MEMBER_BLOCK_ALREADY_EXISTS
                );
            }

            throw exception;
        }

        friendshipRepository.findBetweenMembers(
                blockerId,
                blockedId
        ).ifPresent(friendshipRepository::delete);

        return MemberEvent.hidden(blockerId);
    }

    @Transactional
    public void unblock(
            Long blockerId,
            Long blockedId
    ) {
        validateDifferentMembers(blockerId, blockedId);
        findMember(blockerId);

        MemberBlock memberBlock = memberBlockRepository
                .findByBlockerIdAndBlockedId(
                        blockerId,
                        blockedId
                )
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.MEMBER_BLOCK_NOT_FOUND
                        )
                );

        memberBlockRepository.delete(memberBlock);
    }

    @Transactional(readOnly = true)
    public List<BlockedMemberResponse> getBlockedMembers(
            Long blockerId
    ) {
        findMember(blockerId);

        return memberBlockRepository.findAllByBlockerId(blockerId)
                .stream()
                .map(BlockedMemberResponse::from)
                .toList();
    }

    // === private method ===

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.MEMBER_NOT_FOUND
                        )
                );
    }

    private void validateDifferentMembers(
            Long blockerId,
            Long blockedId
    ) {
        if (blockerId.equals(blockedId)) {
            throw new CustomException(
                    ErrorCode.SELF_BLOCK_NOT_ALLOWED
            );
        }
    }

    private boolean isMemberBlockConflict(
            DataIntegrityViolationException exception
    ) {
        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintException) {
                return "uq_member_blocks_blocker_id_blocked_id".equals(
                        constraintException.getConstraintName()
                );
            }

            cause = cause.getCause();
        }

        return false;
    }
}
