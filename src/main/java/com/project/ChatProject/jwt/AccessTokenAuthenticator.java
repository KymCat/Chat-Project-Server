package com.project.ChatProject.jwt;


import com.project.ChatProject.jwt.refresh.RefreshTokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AccessTokenAuthenticator {

    private final JwtProvider jwtProvider;
    private final AccessTokenBlacklistStore blacklistStore;
    private final RefreshTokenStore refreshTokenStore;

    public Authentication authenticate(String accessToken) {
        AccessTokenClaims claims =
                jwtProvider.parseAccessToken(accessToken);

        if (blacklistStore.exists(claims.tokenId()))
            throw new BadCredentialsException(
                    "Blacklisted access token"
            );

        if (!refreshTokenStore.isActiveSession(
                claims.memberId(),
                claims.sessionId()
        )) {
            throw new BadCredentialsException(
                    "Invalid Session"
            );
        }

        return new UsernamePasswordAuthenticationToken(
                claims,
                null,
                List.of()
        );


    }
}
