package com.project.ChatProject.jwt;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private AccessTokenAuthenticator authenticator;

    @Mock
    private JwtAuthenticationEntryPoint authenticationEntryPoint;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter jwtAuthenticationFilter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        jwtAuthenticationFilter = new JwtAuthenticationFilter(
                authenticator,
                authenticationEntryPoint
        );
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validAccessTokenStoresAuthenticationInSecurityContext() throws Exception {
        AccessTokenClaims claims = claims();
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        claims,
                        null,
                        List.of()
                );
        request.addHeader("Authorization", "Bearer access-token");
        when(authenticator.authenticate("access-token"))
                .thenReturn(authentication);

        jwtAuthenticationFilter.doFilterInternal(
                request,
                response,
                filterChain
        );

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isSameAs(authentication);
        verify(filterChain).doFilter(request, response);
        verify(authenticationEntryPoint, never()).commence(
                any(),
                any(),
                any()
        );
    }

    @Test
    void invalidAccessTokenIsRejected() throws Exception {
        request.addHeader("Authorization", "Bearer access-token");
        when(authenticator.authenticate("access-token"))
                .thenThrow(
                        new BadCredentialsException(
                                "Blacklisted access token"
                        )
                );

        jwtAuthenticationFilter.doFilterInternal(
                request,
                response,
                filterChain
        );

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNull();
        verify(authenticationEntryPoint).commence(
                eq(request),
                eq(response),
                any(BadCredentialsException.class)
        );
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void skipsOnlyPostPublicAuthenticationEndpoints() {
        assertThat(shouldNotFilter("POST", "/member/signup")).isTrue();
        assertThat(shouldNotFilter("POST", "/auth/login")).isTrue();
        assertThat(shouldNotFilter("POST", "/auth/reissue")).isTrue();

        assertThat(shouldNotFilter("POST", "/auth/logout")).isFalse();
        assertThat(shouldNotFilter("POST", "/auth/email-verifications")).isFalse();
        assertThat(shouldNotFilter(
                "POST",
                "/auth/email-verifications/confirm"
        )).isFalse();
        assertThat(shouldNotFilter("GET", "/auth/reissue")).isFalse();
        assertThat(shouldNotFilter("GET", "/members/me")).isFalse();
    }

    private boolean shouldNotFilter(String method, String uri) {
        MockHttpServletRequest filterRequest = new MockHttpServletRequest(
                method,
                uri
        );
        return jwtAuthenticationFilter.shouldNotFilter(filterRequest);
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
