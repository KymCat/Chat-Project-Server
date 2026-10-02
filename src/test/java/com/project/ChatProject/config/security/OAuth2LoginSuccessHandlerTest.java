package com.project.ChatProject.config.security;

import com.project.ChatProject.dto.response.TokenResponse;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.enums.SocialProvider;
import com.project.ChatProject.oauth.GoogleOidcProfileMapper;
import com.project.ChatProject.oauth.OAuthLoginCodeStore;
import com.project.ChatProject.oauth.SocialUserProfile;
import com.project.ChatProject.service.AuthTokenService;
import com.project.ChatProject.service.SocialLoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuth2LoginSuccessHandlerTest {

    @Mock
    private GoogleOidcProfileMapper googleOidcProfileMapper;

    @Mock
    private SocialLoginService socialLoginService;

    @Mock
    private AuthTokenService authTokenService;

    @Mock
    private OAuthLoginCodeStore loginCodeStore;

    @Mock
    private AuthCookieFactory authCookieFactory;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private OAuth2AuthenticationToken authentication;

    @Mock
    private OidcUser oidcUser;

    @InjectMocks
    private OAuth2LoginSuccessHandler successHandler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                successHandler,
                "frontendCallbackUri",
                "http://localhost:5173/oauth/callback"
        );
    }

    @Test
    void googleLoginCreatesTokensCookiesAndRedirectsWithLoginCode()
            throws Exception {
        SocialUserProfile profile = new SocialUserProfile(
                SocialProvider.GOOGLE,
                "google-user-id",
                "user@example.com",
                true,
                "Google User",
                "https://example.com/profile.png"
        );
        Member member = org.mockito.Mockito.mock(Member.class);
        TokenResponse tokenResponse = new TokenResponse(
                "access-token",
                "refresh-token",
                "session-id"
        );

        when(authentication.getAuthorizedClientRegistrationId())
                .thenReturn("google");
        when(authentication.getPrincipal()).thenReturn(oidcUser);
        when(googleOidcProfileMapper.map(oidcUser)).thenReturn(profile);
        when(socialLoginService.login(profile)).thenReturn(member);
        when(authTokenService.create(member)).thenReturn(tokenResponse);
        when(loginCodeStore.create("session-id", "access-token"))
                .thenReturn("login-code");
        when(authCookieFactory.createSessionIdCookie("session-id"))
                .thenReturn("session-id-cookie");
        when(authCookieFactory.createRefreshTokenCookie("refresh-token"))
                .thenReturn("refresh-token-cookie");

        successHandler.onAuthenticationSuccess(
                request,
                response,
                authentication
        );

        InOrder inOrder = inOrder(
                googleOidcProfileMapper,
                socialLoginService,
                authTokenService,
                loginCodeStore
        );
        inOrder.verify(googleOidcProfileMapper).map(oidcUser);
        inOrder.verify(socialLoginService).login(profile);
        inOrder.verify(authTokenService).create(member);
        inOrder.verify(loginCodeStore).create(
                "session-id",
                "access-token"
        );

        verify(response).addHeader(
                HttpHeaders.SET_COOKIE,
                "session-id-cookie"
        );
        verify(response).addHeader(
                HttpHeaders.SET_COOKIE,
                "refresh-token-cookie"
        );
        verify(response).sendRedirect(
                "http://localhost:5173/oauth/callback?code=login-code"
        );
    }

    @Test
    void unsupportedProviderStopsLoginProcessing() {
        when(authentication.getAuthorizedClientRegistrationId())
                .thenReturn("unsupported-provider");
        when(authentication.getPrincipal()).thenReturn(oidcUser);

        assertThatThrownBy(() -> successHandler.onAuthenticationSuccess(
                request,
                response,
                authentication
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported-provider");

        verify(googleOidcProfileMapper, never()).map(oidcUser);
        verify(socialLoginService, never()).login(
                org.mockito.ArgumentMatchers.any()
        );
        verify(authTokenService, never()).create(
                org.mockito.ArgumentMatchers.any()
        );
        verify(loginCodeStore, never()).create(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }
}
