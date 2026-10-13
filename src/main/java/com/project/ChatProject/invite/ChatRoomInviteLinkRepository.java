package com.project.ChatProject.invite;

import java.time.Duration;
import java.util.Optional;

public interface ChatRoomInviteLinkRepository {

    void saveOrReplace(ChatRoomInviteLink link, Duration ttl);

    Optional<ChatRoomInviteLink> findByRoomId(Long roomId);

    Optional<ChatRoomInviteLink> findByToken(String token);

    void deleteByRoomId(Long roomId);
}
