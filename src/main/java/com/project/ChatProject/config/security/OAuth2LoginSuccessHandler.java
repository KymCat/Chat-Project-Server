package com.project.ChatProject.config.security;

import com.project.ChatProject.dto.response.TokenResponse;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.oauth.GoogleOidcProfileMapper;
import com.project.ChatProject.oauth.OAuthLoginCodeStore;
import com.project.ChatProject.oauth.SocialUserProfile;
import com.project.ChatProject.service.AuthTokenService;
import com.project.ChatProject.service.SocialLoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final GoogleOidcProfileMapper googleOidcProfileMapper;
    private final SocialLoginService socialLoginService;
    private final AuthTokenService authTokenService;
    private final OAuthLoginCodeStore loginCodeStore;
    private final AuthCookieFactory authCookieFactory;

    @Value("${oauth2.frontend-callback-uri}")
    private String frontendCallbackUri;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {

        OAuth2AuthenticationToken oauthToken =
                (OAuth2AuthenticationToken) authentication;

        String registerId = oauthToken.getAuthorizedClientRegistrationId();
        OidcUser oidcUser = (OidcUser) oauthToken.getPrincipal();

        SocialUserProfile profile = switchProfile(oidcUser, registerId);

        // 소셜 로그인 및 토큰 발급
        Member member = socialLoginService.login(profile);
        TokenResponse tokenResponse = authTokenService.create(member);

        // redirect URL에 넣을 일회용 loginCode를 생성
        String loginCode = loginCodeStore.create(
                tokenResponse.sessionId(),
                tokenResponse.accessToken()
        );

        // sessionId cookie와 refreshToken cookie를 생성
        String sessionCookie =
                authCookieFactory.createSessionIdCookie(tokenResponse.sessionId());
        String refreshTokenCookie =
                authCookieFactory.createRefreshTokenCookie(tokenResponse.refreshToken());

        // Set-Cookie header를 추가
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie);
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie);


        // {frontendCallbackUri}?code={loginCode} 생성
        String redirectUri = UriComponentsBuilder
                .fromUriString(frontendCallbackUri)
                .queryParam("code", loginCode)
                .build()
                .encode()
                .toUriString();

        response.sendRedirect(redirectUri);
    }

    private SocialUserProfile switchProfile(OidcUser oidcUser, String registrationId) {
        return switch (registrationId) {
            case "google" -> googleOidcProfileMapper.map(oidcUser);

            default -> throw new IllegalArgumentException(
                    "지원하지 않는 OAuth2 provider입니다: "
                            + registrationId
            );
        };
    }
}
