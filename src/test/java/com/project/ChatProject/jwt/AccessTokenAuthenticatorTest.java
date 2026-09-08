package com.project.ChatProject.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessTokenAuthenticatorTest {

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private AccessTokenBlacklistStore blacklistStore;

    private AccessTokenAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        authenticator = new AccessTokenAuthenticator(
                jwtProvider,
                blacklistStore
        );
    }

    @Test
    void validAccessTokenReturnsAuthentication() {
        AccessTokenClaims claims = claims();
        when(jwtProvider.parseAccessToken("access-token"))
                .thenReturn(claims);
        when(blacklistStore.exists("token-id"))
                .thenReturn(false);

        Authentication authentication =
                authenticator.authenticate("access-token");

        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isSameAs(claims);
        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.getAuthorities()).isEmpty();
    }

    @Test
    void blacklistedAccessTokenIsRejected() {
        AccessTokenClaims claims = claims();
        when(jwtProvider.parseAccessToken("access-token"))
                .thenReturn(claims);
        when(blacklistStore.exists("token-id"))
                .thenReturn(true);

        assertThatThrownBy(() ->
                authenticator.authenticate("access-token")
        )
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Blacklisted access token");
    }

    private AccessTokenClaims claims() {
        return new AccessTokenClaims(
                1L,
                "token-id",
                "session-id",
                true,
                Instant.now(),
                Instant.now().plusSeconds(600)
        );
    }
}
