package com.project.ChatProject.jwt.refresh;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class RefreshTokenStore {
    private static final String REFRESH_TOKEN_KEY_PREFIX = "auth:refresh:";
    private static final String MEMBER_SESSIONS_KEY_PREFIX = "auth:member:sessions:";
    private static final String MEMBER_ID_FIELD = "memberId";
    private static final String REFRESH_TOKEN_HASH_FIELD = "refreshTokenHash";

    private final StringRedisTemplate redisTemplate;
    private final RefreshTokenProperties refreshTokenProperties;

    public void save(
            String sessionId,
            Long memberId,
            String refreshTokenHash
    )
    {
        String refreshTokenKey = createRefreshTokenKey(sessionId);
        String memberSessionsKey = createMemberSessionsKey(memberId);
        Duration expiration = refreshTokenProperties.expiration();

        // put(Key, HashKey, Value)
        redisTemplate.opsForHash().put(
                refreshTokenKey,
                MEMBER_ID_FIELD,
                memberId.toString()
        );

        redisTemplate.opsForHash().put(
                refreshTokenKey,
                REFRESH_TOKEN_HASH_FIELD,
                refreshTokenHash
        );

        redisTemplate.expire(
                refreshTokenKey,
                expiration
        );

        redisTemplate.opsForSet().add(
                memberSessionsKey,
                sessionId
        );

        redisTemplate.expire(
                memberSessionsKey,
                expiration
        );
    }

    private String createRefreshTokenKey(String sessionId) {
        return REFRESH_TOKEN_KEY_PREFIX
                + "{"
                + sessionId
                + "}";
    }

    private String createMemberSessionsKey(Long memberId) {
        return MEMBER_SESSIONS_KEY_PREFIX
                + "{"
                + memberId
                + "}";
    }

    // Session Id에 해당하는 Hash Value 찾기
    public Optional<RefreshTokenSession> findBySessionId(String sessionId) {
        String refreshTokenKey = createRefreshTokenKey(sessionId);

        Object memberIdValue = redisTemplate
                .opsForHash()
                .get(refreshTokenKey, MEMBER_ID_FIELD);

        Object refreshTokenHashValue = redisTemplate
                .opsForHash()
                .get(refreshTokenKey, REFRESH_TOKEN_HASH_FIELD);

        if (memberIdValue == null || refreshTokenHashValue == null)
            return Optional.empty();

        RefreshTokenSession session = new RefreshTokenSession(
                Long.valueOf(memberIdValue.toString()),
                refreshTokenHashValue.toString()
        );

        return Optional.of(session);
    }

    // 세션 삭제
    public void deleteBySessionId(String sessionId) {
        Optional<RefreshTokenSession> session =
                findBySessionId(sessionId);

        redisTemplate.delete(createRefreshTokenKey(sessionId));

        session.ifPresent(savedSession ->
                redisTemplate.opsForSet().remove(
                        createMemberSessionsKey(savedSession.memberId()),
                        sessionId
                ));
    }

    // 모든 기기 세션 삭제
    public void deleteAllByMemberId(Long memberId) {
        String memberSessionsKey = createMemberSessionsKey(memberId);
        Set<String> sessionIds = redisTemplate
                .opsForSet()
                .members(memberSessionsKey);

        if (sessionIds != null) {
            for (String sessionId : sessionIds) {
                redisTemplate.delete(createRefreshTokenKey(sessionId));
            }
        }

        redisTemplate.delete(memberSessionsKey);
    }
}
