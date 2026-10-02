package com.project.ChatProject.controller;

import com.project.ChatProject.config.security.AuthCookieFactory;
import com.project.ChatProject.dto.request.EmailVerificationConfirmRequest;
import com.project.ChatProject.dto.request.LoginRequest;
import com.project.ChatProject.dto.request.OAuthLoginCodeExchangeRequest;
import com.project.ChatProject.dto.response.ApiResponse;
import com.project.ChatProject.dto.response.TokenResponse;
import com.project.ChatProject.jwt.AccessTokenClaims;
import com.project.ChatProject.service.AuthService;
import com.project.ChatProject.service.EmailVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private AuthCookieFactory authCookieFactory;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController(
                authService,
                emailVerificationService,
                authCookieFactory
        );
    }

    @Test
    void loginReturnsAccessTokenAndAuthenticationCookies() {
        LoginRequest request = new LoginRequest(
                "user@example.com",
                "password123!"
        );
        when(authService.login(request)).thenReturn(
                new TokenResponse(
                        "access-token",
                        "refresh-token",
                        "session-id"
                )
        );
        when(authCookieFactory.createSessionIdCookie("session-id"))
                .thenReturn("session-id-cookie");
        when(authCookieFactory.createRefreshTokenCookie("refresh-token"))
                .thenReturn("refresh-token-cookie");

        ResponseEntity<ApiResponse<String>> response =
                authController.login(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data()).isEqualTo("access-token");

        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).containsExactly(
                "session-id-cookie",
                "refresh-token-cookie"
        );
        verify(authCookieFactory).createSessionIdCookie("session-id");
        verify(authCookieFactory).createRefreshTokenCookie("refresh-token");
    }

    @Test
    void logoutExpiresAuthenticationCookies() {
        AccessTokenClaims claims = new AccessTokenClaims(
                1L,
                "token-id",
                "session-id",
                true,
                Instant.now(),
                Instant.now().plusSeconds(600)
        );
        when(authCookieFactory.deleteSessionIdCookie())
                .thenReturn("deleted-session-id-cookie");
        when(authCookieFactory.deleteRefreshTokenCookie())
                .thenReturn("deleted-refresh-token-cookie");

        ResponseEntity<ApiResponse<Void>> response =
                authController.logout(claims);

        verify(authService).logout(claims);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();

        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).containsExactly(
                "deleted-session-id-cookie",
                "deleted-refresh-token-cookie"
        );
        verify(authCookieFactory).deleteSessionIdCookie();
        verify(authCookieFactory).deleteRefreshTokenCookie();
    }

    @Test
    void reissueReturnsRotatedAccessTokenAndRefreshTokenCookie() {
        when(authService.reissue(
                "session-id",
                "current-refresh-token"
        )).thenReturn(
                new TokenResponse(
                        "new-access-token",
                        "new-refresh-token",
                        "session-id"
                )
        );
        when(authCookieFactory.createSessionIdCookie("session-id"))
                .thenReturn("session-id-cookie");
        when(authCookieFactory.createRefreshTokenCookie("new-refresh-token"))
                .thenReturn("new-refresh-token-cookie");

        ResponseEntity<ApiResponse<String>> response =
                authController.reissue(
                        "session-id",
                        "current-refresh-token"
                );

        verify(authService).reissue(
                "session-id",
                "current-refresh-token"
        );
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data()).isEqualTo("new-access-token");

        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).containsExactly(
                "session-id-cookie",
                "new-refresh-token-cookie"
        );
        verify(authCookieFactory).createSessionIdCookie("session-id");
        verify(authCookieFactory)
                .createRefreshTokenCookie("new-refresh-token");
    }

    @Test
    void requestEmailVerificationDelegatesAuthenticatedMemberId() {
        AccessTokenClaims claims = claims();

        ResponseEntity<ApiResponse<Void>> response =
                authController.request(claims);

        verify(emailVerificationService).request(1L);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data()).isNull();
    }

    @Test
    void confirmEmailVerificationDelegatesMemberIdAndCode() {
        AccessTokenClaims claims = claims();
        EmailVerificationConfirmRequest request =
                new EmailVerificationConfirmRequest("123456");

        ResponseEntity<ApiResponse<Void>> response =
                authController.confirmEmailVerification(
                        claims,
                        request
                );

        verify(emailVerificationService).confirm(1L, "123456");
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data()).isNull();
    }

    @Test
    void exchangeOAuthLoginCodeReturnsAccessTokenWithoutNewCookies() {
        OAuthLoginCodeExchangeRequest request =
                new OAuthLoginCodeExchangeRequest("login-code");
        when(authService.exchangeOAuthLoginCode(
                "login-code",
                "session-id"
        )).thenReturn("access-token");

        ResponseEntity<ApiResponse<String>> response =
                authController.exchangeOAuthLoginCode(
                        "session-id",
                        request
                );

        verify(authService).exchangeOAuthLoginCode(
                "login-code",
                "session-id"
        );
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data()).isEqualTo("access-token");
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
    }

    private AccessTokenClaims claims() {
        return new AccessTokenClaims(
                1L,
                "token-id",
                "session-id",
                false,
                Instant.now(),
                Instant.now().plusSeconds(600)
        );
    }
}
