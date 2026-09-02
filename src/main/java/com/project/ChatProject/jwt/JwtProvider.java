package com.project.ChatProject.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtProvider {
    private static final String SESSION_ID_CLAIM = "sid";
    private static final String EMAIL_VERIFIED_CLAIM = "email_verified";

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey ;
    private final JwtParser jwtParser;

    public JwtProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;

        byte[] secretBytes =
                Decoders.BASE64.decode(jwtProperties.secret());

        this.signingKey  = Keys.hmacShaKeyFor(secretBytes);
        this.jwtParser = Jwts.parser()
                .verifyWith(signingKey)
                .build();
    }

    public String generateAccessToken(
            Long memberId,
            String sessionId,
            boolean emailVerified
    )
    {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(
                jwtProperties.accessTokenExpiration());

        return Jwts.builder()
                .subject(memberId.toString())
                .id(UUID.randomUUID().toString())
                .claim(SESSION_ID_CLAIM, sessionId)
                .claim(EMAIL_VERIFIED_CLAIM, emailVerified)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public AccessTokenClaims parseAccessToken(String accessToken) {
        Claims claims = jwtParser
                .parseSignedClaims(accessToken)
                .getPayload();

        return new AccessTokenClaims(
                Long.valueOf(claims.getSubject()),
                claims.getId(),
                claims.get(SESSION_ID_CLAIM, String.class),
                Boolean.TRUE.equals(
                        claims.get(EMAIL_VERIFIED_CLAIM, Boolean.class)
                ),
                claims.getIssuedAt().toInstant(),
                claims.getExpiration().toInstant()
        );
    }
}

