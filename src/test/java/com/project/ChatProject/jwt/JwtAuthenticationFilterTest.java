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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private AccessTokenBlacklistStore accessTokenBlacklistStore;

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
                jwtProvider,
                accessTokenBlacklistStore,
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
        request.addHeader("Authorization", "Bearer access-token");
        when(jwtProvider.parseAccessToken("access-token"))
                .thenReturn(claims);
        when(accessTokenBlacklistStore.exists("token-id"))
                .thenReturn(false);

        jwtAuthenticationFilter.doFilterInternal(
                request,
                response,
                filterChain
        );

        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isSameAs(claims);
        assertThat(authentication.isAuthenticated()).isTrue();
        verify(filterChain).doFilter(request, response);
        verify(authenticationEntryPoint, never()).commence(
                any(),
                any(),
                any()
        );
    }

    @Test
    void blacklistedAccessTokenIsRejected() throws Exception {
        AccessTokenClaims claims = claims();
        request.addHeader("Authorization", "Bearer access-token");
        when(jwtProvider.parseAccessToken("access-token"))
                .thenReturn(claims);
        when(accessTokenBlacklistStore.exists("token-id"))
                .thenReturn(true);

        jwtAuthenticationFilter.doFilterInternal(
                request,
                response,
                filterChain
        );

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNull();
        verify(authenticationEntryPoint).commence(
                org.mockito.ArgumentMatchers.eq(request),
                org.mockito.ArgumentMatchers.eq(response),
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
