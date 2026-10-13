package com.project.ChatProject.invite;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RedisChatRoomInviteLinkRepository
        implements ChatRoomInviteLinkRepository {

    private static final String ROOM_ID_KEY = "chat-room:invite:room:";
    private static final String TOKEN_KEY = "chat-room:invite:token:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void saveOrReplace(ChatRoomInviteLink link, Duration ttl) {

        String roomIdKey = createRoomIdKey(link.roomId());
        String newTokenKey = createTokenKey(link.token());

        String oldToken = redisTemplate.opsForValue().get(roomIdKey);
        if (oldToken != null) {
            String oldTokenKey = createTokenKey(oldToken);
            redisTemplate.delete(oldTokenKey);
        }

        /*
            key     : roomId
            value   : token
         */
        redisTemplate.opsForValue().set(
                roomIdKey,
                link.token(),
                ttl
        );

        /*
            key     : token
            value   :  [ roomId, expiresAt ]
         */
        redisTemplate.opsForHash().putAll(
                newTokenKey,
                Map.of(
                        "roomId", link.roomId().toString(),
                        "expiresAt", link.expiresAt().toString()
                )
        );
        redisTemplate.expire(newTokenKey, ttl);
    }

    @Override
    public Optional<ChatRoomInviteLink> findByRoomId(Long roomId) {
        String token = redisTemplate
                .opsForValue()
                .get(createRoomIdKey(roomId));

        if (token == null) {
            return Optional.empty();
        }

        return findByToken(token);
    }

    @Override
    public Optional<ChatRoomInviteLink> findByToken(String token) {
        String tokenKey = createTokenKey(token);

        Object roomIdValue = redisTemplate
                .opsForHash()
                .get(tokenKey, "roomId");

        Object expiresAtValue = redisTemplate
                .opsForHash()
                .get(tokenKey, "expiresAt");

        if (roomIdValue == null || expiresAtValue == null) {
            return Optional.empty();
        }

        return Optional.of(
                ChatRoomInviteLink.of(
                        Long.valueOf(roomIdValue.toString()),
                        token,
                        Instant.parse(expiresAtValue.toString())
                )
        );
    }

    @Override
    public void deleteByRoomId(Long roomId) {
        String roomIdKey = createRoomIdKey(roomId);
        String token = redisTemplate
                .opsForValue()
                .get(roomIdKey);

        if (token == null) {
            return;
        }

        redisTemplate.delete(
                List.of(
                        roomIdKey,
                        createTokenKey(token)
                )
        );
    }

    // ==== private method ====

    private String createRoomIdKey(Long roomId) {
        return ROOM_ID_KEY
                + "{"
                + roomId
                + "}";
    }

    private String createTokenKey(String token) {
        return TOKEN_KEY
                + "{"
                + token
                + "}";
    }
}
