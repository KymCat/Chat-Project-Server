package com.project.ChatProject.jwt;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderTest {

    private static final String TEST_SECRET = Base64.getEncoder()
            .encodeToString(
                    "01234567890123456789012345678901"
                            .getBytes(StandardCharsets.UTF_8)
            );

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties(
                Duration.ofMinutes(15),
                TEST_SECRET
        );

        jwtProvider = new JwtProvider(jwtProperties);
    }

    @Test
    void generateAndParseAccessToken() {
        Long memberId = 1L;
        String sessionId = "test-session-id";
        boolean emailVerified = false;

        String accessToken = jwtProvider.generateAccessToken(
                memberId,
                sessionId,
                emailVerified
        );

        AccessTokenClaims claims = jwtProvider.parseAccessToken(accessToken);

        assertThat(claims.memberId()).isEqualTo(memberId);
        assertThat(claims.sessionId()).isEqualTo(sessionId);
        assertThat(claims.emailVerified()).isFalse();
        assertThat(claims.tokenId()).isNotBlank();
        assertThat(claims.issuedAt()).isNotNull();
        assertThat(claims.expiresAt()).isAfter(claims.issuedAt());
    }

    @Test
    void generateAccessTokenReturnsDifferentTokenId() {
        String firstToken = jwtProvider.generateAccessToken(
                1L,
                "test-session-id",
                false
        );

        String secondToken = jwtProvider.generateAccessToken(
                1L,
                "test-session-id",
                false
        );

        AccessTokenClaims firstClaims = jwtProvider.parseAccessToken(firstToken);
        AccessTokenClaims secondClaims = jwtProvider.parseAccessToken(secondToken);

        assertThat(firstClaims.tokenId())
                .isNotEqualTo(secondClaims.tokenId());
    }

    @Test
    void parseAccessTokenRejectsModifiedToken() {
        String accessToken = jwtProvider.generateAccessToken(
                1L,
                "test-session-id",
                false
        );

        String modifiedToken = accessToken + "x";

        assertThatThrownBy(() ->
                jwtProvider.parseAccessToken(modifiedToken)
        ).isInstanceOf(JwtException.class);
    }

    @Test
    void parseAccessTokenRejectsExpiredToken() {
        JwtProvider expiredTokenProvider = new JwtProvider(
                new JwtProperties(
                        Duration.ofSeconds(-1),
                        TEST_SECRET
                )
        );

        String expiredToken = expiredTokenProvider.generateAccessToken(
                1L,
                "test-session-id",
                false
        );

        assertThatThrownBy(() ->
                expiredTokenProvider.parseAccessToken(expiredToken)
        ).isInstanceOf(JwtException.class);
    }
}
