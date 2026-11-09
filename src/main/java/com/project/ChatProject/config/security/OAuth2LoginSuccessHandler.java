package com.project.ChatProject.config.security;

import com.project.ChatProject.dto.response.TokenResponse;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.oauth.GoogleOidcProfileMapper;
import com.project.ChatProject.oauth.OAuthLoginCodeStore;
import com.project.ChatProject.oauth.SocialUserProfile;
import com.project.ChatProject.service.AuthTokenService;
import com.project.ChatProject.service.AuthService;
import com.project.ChatProject.service.MemberService;
import com.project.ChatProject.service.SocialLoginService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.WebUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final GoogleOidcProfileMapper googleOidcProfileMapper;
    private final SocialLoginService socialLoginService;
    private final AuthTokenService authTokenService;
    private final OAuthLoginCodeStore loginCodeStore;
    private final AuthCookieFactory authCookieFactory;
    private final MemberService memberService;
    private final AuthService authService;

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

        if (handleSocialReauthentication(
                request,
                response,
                profile
        )) {
            return;
        }

        // 소셜 로그인 및 토큰 발급
        Member member = socialLoginService.login(profile);

        initializeSocialProfileImage(
                member,
                profile
        );

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

    // === private method ===

    private boolean handleSocialReauthentication(
            HttpServletRequest request,
            HttpServletResponse response,
            SocialUserProfile profile
    ) throws IOException {
        Cookie intentCookie = WebUtils.getCookie(
                request,
                AuthCookieFactory
                        .REAUTHENTICATION_INTENT_COOKIE_NAME
        );

        if (intentCookie == null
                || !StringUtils.hasText(intentCookie.getValue())) {
            return false;
        }

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                authCookieFactory
                        .deleteReauthenticationIntentCookie()
        );

        String redirectUri;
        try {
            String reauthenticationCode =
                    authService.completeSocialReauthentication(
                            intentCookie.getValue(),
                            profile
                    );

            redirectUri = UriComponentsBuilder
                    .fromUriString(frontendCallbackUri)
                    .queryParam(
                            "reauthCode",
                            reauthenticationCode
                    )
                    .build()
                    .encode()
                    .toUriString();
        } catch (RuntimeException exception) {
            log.warn(
                    "Social reauthentication failed",
                    exception
            );

            redirectUri = UriComponentsBuilder
                    .fromUriString(frontendCallbackUri)
                    .queryParam("reauthError", true)
                    .build()
                    .encode()
                    .toUriString();
        }

        response.sendRedirect(redirectUri);
        return true;
    }

    private void initializeSocialProfileImage(
            Member member,
            SocialUserProfile profile
    ) {
        try {
            memberService.initializeSocialProfileImageIfAbsent(
                    member.getId(),
                    profile.profileImageUrl()
            );
        } catch (RuntimeException exception) {
            /*
             * 프로필 이미지 초기화는 로그인 핵심 기능이 아니다.
             * 다운로드, 검증, S3 또는 DB 저장에 실패해도 로그인은 계속한다.
             */
            log.warn(
                    "Social profile image initialization failed, memberId={}",
                    member.getId(),
                    exception
            );
        }
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
