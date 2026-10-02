package com.project.ChatProject.config.security;

import com.project.ChatProject.jwt.refresh.RefreshTokenProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class AuthCookieFactory {

    private final RefreshTokenProperties refreshTokenProperties;

    @Value("${cookie.secure}")
    private boolean secure;

    public String createSessionIdCookie(String value) {
        String cookieName = "sessionId";

        return createCookie(cookieName, value);
    }

    public String createRefreshTokenCookie(String value) {
        String cookieName = "refreshToken";

        return createCookie(cookieName, value);
    }

    public String deleteSessionIdCookie() {
        String cookieName = "sessionId";

        return createCookie(cookieName, "");
    }

    public String deleteRefreshTokenCookie() {
        String cookieName = "refreshToken";

        return createCookie(cookieName, "");
    }

    private String createCookie(String cookieName, String value) {
        Duration expiration = StringUtils.hasLength(value)
                ? refreshTokenProperties.expiration()
                : Duration.ZERO;

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
