package com.project.ChatProject.service;

import com.project.ChatProject.entity.Friendship;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.enums.FriendshipStatus;
import com.project.ChatProject.entity.enums.MemberStatus;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.repository.FriendshipRepository;
import com.project.ChatProject.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FriendshipService {

    private final MemberRepository memberRepository;
    private final FriendshipRepository friendshipRepository;

    @Transactional
    public void requestFriend(
            Long memberId,
            Long targetMemberId
    ) {
        if (memberId.equals(targetMemberId)) {
            throw new CustomException(
                    ErrorCode.SELF_FRIEND_REQUEST_NOT_ALLOWED
            );
        }

        Member requester = validateMember(memberId);
        Member receiver = validateMember(targetMemberId);

        // 이미 친구인지 검사
        if (friendshipRepository.existsBetweenMembersAndStatus(
                memberId,
                targetMemberId,
                FriendshipStatus.ACCEPTED
        )) {
            throw new CustomException(
                    ErrorCode.ALREADY_FRIENDS
            );
        }

        // 이미 친구 요청을 보냈는지 검사
        if (friendshipRepository.existsBetweenMembersAndStatus(
                memberId,
                targetMemberId,
                FriendshipStatus.PENDING
        )) {
            throw new CustomException(
                    ErrorCode.FRIEND_REQUEST_EXISTS
            );
        }

        // 동시 친구 요청에 대한 처리
        try {
            friendshipRepository.saveAndFlush(
                    Friendship.create(requester, receiver)
            );
        } catch (DataIntegrityViolationException exception) {
            if (isFriendshipPairConflict(exception)) {
                throw new CustomException(
                        ErrorCode.FRIEND_RELATION_ALREADY_EXISTS
                );
            }

            throw exception;
        }
    }

    // === private method ===

    private Member validateMember(Long memberId) {
        Member member = memberRepository
                .findById(memberId)
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.MEMBER_NOT_FOUND
                        )
                );

        if (member.getStatus().equals(MemberStatus.SUSPENDED)) {
            throw new CustomException(
                    ErrorCode.MEMBER_BLOCKED
            );
        }

        if (member.getStatus().equals(MemberStatus.WITHDRAWN)) {
            throw new CustomException(
                    ErrorCode.MEMBER_WITHDRAWN
            );
        }

        return member;
    }

    /**
     * 친구 요청이 동시에 들어왔을 때 DB 제약조건 위반인지 확인
     * @param exception
     * @return
     */
    private boolean isFriendshipPairConflict(
            DataIntegrityViolationException exception
    ) {
        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintException) {
                return "uq_friendships_member_pair".equals(
                        constraintException.getConstraintName()
                );
            }

            cause = cause.getCause();
        }

        return false;
    }
}
