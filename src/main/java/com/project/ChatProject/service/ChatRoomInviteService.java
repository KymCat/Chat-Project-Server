package com.project.ChatProject.service;

import com.project.ChatProject.entity.enums.ChatRoomMemberRole;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.invite.ChatRoomInviteLink;
import com.project.ChatProject.invite.ChatRoomInviteLinkRepository;
import com.project.ChatProject.invite.ChatRoomInviteTokenGenerator;
import com.project.ChatProject.invite.InviteLinkExpiration;
import com.project.ChatProject.service.support.ChatRoomParticipationContext;
import com.project.ChatProject.service.support.ParticipationLockMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ChatRoomInviteService {

    private final ChatRoomInviteTokenGenerator inviteLinkGenerator;
    private final ChatRoomInviteLinkRepository inviteLinkRepository;
    private final ChatRoomParticipationService participationService;

    @Transactional
    public ChatRoomInviteLink createInviteLink(
            Long roomId,
            Long memberId,
            InviteLinkExpiration expiration
    ) {
        inviteLinkValidation(roomId, memberId);

        if(inviteLinkRepository.findByRoomId(roomId).isPresent()) {
            throw new CustomException(
                    ErrorCode.CHAT_ROOM_LINK_ALREADY_EXISTS
            );
        }

        return createTokenAndSave(
                roomId,
                expiration
        );
    }

    @Transactional
    public ChatRoomInviteLink reissueInviteLink(
            Long roomId,
            Long memberId,
            InviteLinkExpiration expiration
    ) {
        inviteLinkValidation(roomId, memberId);

        inviteLinkRepository.findByRoomId(roomId)
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.CHAT_ROOM_INVITE_LINK_NOT_FOUND
                        )
                );

        return createTokenAndSave(roomId, expiration);
    }

    @Transactional
    public void deleteInviteLink(
            Long roomId,
            Long memberId
    ) {
        inviteLinkValidation(roomId, memberId);

        if (inviteLinkRepository
                .findByRoomId(roomId)
                .isEmpty()
        ) {
            throw new CustomException(
                    ErrorCode.CHAT_ROOM_INVITE_LINK_NOT_FOUND
            );
        }

        inviteLinkRepository.deleteByRoomId(roomId);
    }

    // ==== private Method ====

    /**
     * 초대링크 토큰 생성과 Redis 저장
     * @param roomId
     * @param expiration
     * @return
     */
    private ChatRoomInviteLink createTokenAndSave(
            Long roomId,
            InviteLinkExpiration expiration
    ) {

        String token = inviteLinkGenerator.generate();
        Duration ttl = expiration.getDuration();
        Instant expiresAt = Instant.now().plus(ttl);

        ChatRoomInviteLink link = ChatRoomInviteLink.of(
                roomId,
                token,
                expiresAt
        );

        inviteLinkRepository
                .saveOrReplace(link);

        return link;
    }

    /**
     * 채팅방, 멤버 검증과 초대링크 생성 권한 확인
     * @param roomId
     * @param memberId
     */
    private void inviteLinkValidation(
            Long roomId,
            Long memberId
    ) {
        ChatRoomParticipationContext context = participationService
                .requireParticipation(
                        roomId,
                        memberId,
                        ParticipationLockMode.CHAT_ROOM
                );

        // 방장 권한 확인
        if (!context.chatRoomMember()
                .getRole()
                .equals(ChatRoomMemberRole.OWNER)
        ) {
            throw new CustomException(
                    ErrorCode.CHAT_ROOM_OWNER_REQUIRED
            );
        }
    }
}
