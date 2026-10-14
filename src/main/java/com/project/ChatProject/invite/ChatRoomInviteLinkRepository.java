package com.project.ChatProject.invite;

import java.util.Optional;

public interface ChatRoomInviteLinkRepository {

    void saveOrReplace(ChatRoomInviteLink link);

    Optional<ChatRoomInviteLink> findByRoomId(Long roomId);

    Optional<ChatRoomInviteLink> findByToken(String token);

    void deleteByRoomId(Long roomId);
}
