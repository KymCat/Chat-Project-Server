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

        ChatRoomParticipationContext context = participationService
                .requireParticipation(
                        roomId,
                        memberId,
                        ParticipationLockMode.CHAT_ROOM
                );

        if (!context.chatRoomMember()
                .getRole()
                .equals(ChatRoomMemberRole.OWNER)
        ) {
            throw new CustomException(
                    ErrorCode.CHAT_ROOM_OWNER_REQUIRED
            );
        }

        if(inviteLinkRepository.findByRoomId(roomId).isPresent()) {
            throw new CustomException(
                    ErrorCode.CHAT_ROOM_LINK_ALREADY_EXISTS
            );
        }

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

//    @Transactional
//    public ChatRoomInviteLink reissueInviteLink(
//            Long roomId,
//            Long memberId,
//            InviteLinkExpiration expiration
//    ) {
//
//    }
}
