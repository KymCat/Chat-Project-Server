package com.project.ChatProject.config.security;

import com.project.ChatProject.jwt.refresh.RefreshTokenProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AuthCookieFactoryTest {

    private AuthCookieFactory authCookieFactory;

    @BeforeEach
    void setUp() {
        authCookieFactory = new AuthCookieFactory(
                new RefreshTokenProperties(Duration.ofDays(14))
        );
        ReflectionTestUtils.setField(
                authCookieFactory,
                "secure",
                true
        );
    }

    @Test
    void createsSessionIdCookieWithAuthenticationCookiePolicy() {
        String cookie =
                authCookieFactory.createSessionIdCookie("session-id");

        assertThat(cookie)
                .contains("sessionId=session-id")
                .contains("Path=/")
                .contains("Max-Age=1209600")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Lax");
    }

    @Test
    void createsRefreshTokenCookieWithAuthenticationCookiePolicy() {
        String cookie =
                authCookieFactory.createRefreshTokenCookie("refresh-token");

        assertThat(cookie)
                .contains("refreshToken=refresh-token")
                .contains("Path=/")
                .contains("Max-Age=1209600")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Lax");
    }

    @Test
    void deletesSessionIdCookieWithZeroMaxAge() {
        String cookie = authCookieFactory.deleteSessionIdCookie();

        assertThat(cookie)
                .contains("sessionId=")
                .contains("Path=/")
                .contains("Max-Age=0")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Lax");
    }

    @Test
    void deletesRefreshTokenCookieWithZeroMaxAge() {
        String cookie = authCookieFactory.deleteRefreshTokenCookie();

        assertThat(cookie)
                .contains("refreshToken=")
                .contains("Path=/")
                .contains("Max-Age=0")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Lax");
    }
}
