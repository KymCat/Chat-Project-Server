package com.project.ChatProject.oauth;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/*
    소셜 로그인 인증 끝난 후 일회용 코드 발급 클래스
 */
@Component
@RequiredArgsConstructor
public class DesktopOAuthCodeStore {

    private static final String KEY_PREFIX = "auth:desktop-oauth-code:";
    private static final Duration TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;

    public String create(Long memberId, String codeChallenge) {

        String code = UUID.randomUUID().toString();

        redisTemplate.opsForValue().set(
                createKey(code, codeChallenge),
                memberId.toString(),
                TTL
        );

        return code;
    }

    public Optional<Long> consume(String code, String codeChallenge) {

        String memberId = redisTemplate.opsForValue()
                .getAndDelete(createKey(code, codeChallenge));

        return Optional.ofNullable(memberId)
                .map(Long::valueOf);
    }

    public String createKey(String code, String codeChallenge) {
        return KEY_PREFIX + code + ":" + codeChallenge;
    }
}
