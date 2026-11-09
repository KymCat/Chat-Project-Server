package com.project.ChatProject.config.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private final AuthCookieFactory authCookieFactory;

    @Value("${oauth2.frontend-callback-uri}")
    private String frontendCallbackUri;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {

        if (WebUtils.getCookie(
                request,
                AuthCookieFactory
                        .REAUTHENTICATION_INTENT_COOKIE_NAME
        ) != null) {
            response.addHeader(
                    HttpHeaders.SET_COOKIE,
                    authCookieFactory
                            .deleteReauthenticationIntentCookie()
            );
        }

        String redirectUri = UriComponentsBuilder
                .fromUriString(frontendCallbackUri)
                .queryParam("oauthError", true)
                .build()
                .encode()
                .toUriString();

        response.sendRedirect(redirectUri);
    }
}
