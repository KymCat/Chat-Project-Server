package com.project.ChatProject.config.security;

import com.project.ChatProject.jwt.refresh.RefreshTokenProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class AuthCookieFactory {

    public static final String REAUTHENTICATION_INTENT_COOKIE_NAME =
            "intentId";

    private final RefreshTokenProperties refreshTokenProperties;

    @Value("${cookie.secure}")
    private boolean secure;

    public String createSessionIdCookie(String value) {
        String cookieName = "sessionId";
        Duration expiration = refreshTokenProperties.expiration();

        return createCookie(
                cookieName,
                value,
                expiration
        );
    }

    public String createRefreshTokenCookie(String value) {
        String cookieName = "refreshToken";
        Duration expiration = refreshTokenProperties.expiration();

        return createCookie(
                cookieName,
                value,
                expiration
        );
    }

    public String createReauthenticationIntentCookie(String value) {
        return createCookie(
                REAUTHENTICATION_INTENT_COOKIE_NAME,
                value,
                Duration.ofMinutes(5)
        );
    }

    public String deleteSessionIdCookie() {
        String cookieName = "sessionId";

        return createCookie(
                cookieName,
                "",
                Duration.ZERO
        );
    }

    public String deleteRefreshTokenCookie() {
        String cookieName = "refreshToken";

        return createCookie(
                cookieName,
                "",
                Duration.ZERO
        );
    }

    public String deleteReauthenticationIntentCookie() {
        return createCookie(
                REAUTHENTICATION_INTENT_COOKIE_NAME,
                "",
                Duration.ZERO
        );
    }

    private String createCookie(
            String cookieName,
            String value,
            Duration expiration
    ) {

        return ResponseCookie
                .from(
                        cookieName,
                        value
                )
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(expiration)
                .sameSite("Lax")
                .build()
                .toString();
    }
}
