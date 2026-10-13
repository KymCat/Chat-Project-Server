package com.project.ChatProject.invite;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RedisChatRoomInviteLinkRepository
        implements ChatRoomInviteLinkRepository {

    private static final String ROOM_ID_KEY = "chat-room:invite:room:";
    private static final String TOKEN_KEY = "chat-room:invite:token:";
    private static final RedisScript<Long> SAVE_OR_REPLACE_SCRIPT =
            loadScript(
                    "redis/chat-room-invite-save-or-replace.lua"
            );

    private static final RedisScript<Long> DELETE_BY_ROOM_ID =
            loadScript(
                    "redis/chat-room-invite-delete-by-room-id.lua"
            );

    private final StringRedisTemplate redisTemplate;

    @Override
    public void saveOrReplace(ChatRoomInviteLink link, Duration ttl) {

        String roomIdKey = createRoomIdKey(link.roomId());
        String newTokenKey = createTokenKey(link.token());

        String oldToken = redisTemplate
                .opsForValue()
                .get(roomIdKey);

        String expectedOldToken = oldToken == null
                ? ""
                : oldToken;

        String oldTokenKey = oldToken == null
                ? newTokenKey   // temp
                : createTokenKey(oldToken);

        Long result = redisTemplate.execute(
                SAVE_OR_REPLACE_SCRIPT,
                List.of(    // key
                        roomIdKey,
                        oldTokenKey,
                        newTokenKey
                ),
                expectedOldToken,
                link.token(),
                link.roomId().toString(),
                link.expiresAt().toString(),
                Long.toString(ttl.toMillis())
        );

        if (!Long.valueOf(1L).equals(result)) {
            throw new IllegalStateException(
                    "초대 링크가 동시에 변경되었습니다."
            );
        }
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

        String tokenKey = createTokenKey(token);
        Long result = redisTemplate.execute(
                DELETE_BY_ROOM_ID,
                List.of(
                        roomIdKey,
                        tokenKey
                ),
                token
        );

        if (!Long.valueOf(1L).equals(result)) {
            throw new IllegalStateException(
                    "초대 링크가 동시에 변경되었습니다."
            );
        }
    }

    // ==== private method ====

    private static RedisScript<Long> loadScript(String path) {
        DefaultRedisScript<Long> script =
                new DefaultRedisScript<>();

        script.setLocation(
                new ClassPathResource(path)
        );

        script.setResultType(Long.class);

        return script;
    }

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
