package com.project.ChatProject.controller;

import com.project.ChatProject.dto.request.LoginRequest;
import com.project.ChatProject.dto.response.ApiResponse;
import com.project.ChatProject.dto.response.TokenResponse;
import com.project.ChatProject.jwt.AccessTokenClaims;
import com.project.ChatProject.jwt.refresh.RefreshTokenProperties;
import com.project.ChatProject.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController(
                authService,
                new RefreshTokenProperties(Duration.ofDays(14))
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

        ResponseEntity<ApiResponse<String>> response =
                authController.login(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data()).isEqualTo("access-token");

        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).hasSize(2);
        assertThat(cookies.get(0))
                .contains("sessionId=session-id")
                .contains("Path=/")
                .contains("Max-Age=1209600")
                .contains("HttpOnly")
                .contains("SameSite=Lax");
        assertThat(cookies.get(1))
                .contains("refreshToken=refresh-token")
                .contains("Path=/")
                .contains("Max-Age=1209600")
                .contains("HttpOnly")
                .contains("SameSite=Lax");
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

        ResponseEntity<ApiResponse<Void>> response =
                authController.logout(claims);

        verify(authService).logout(claims);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();

        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).hasSize(2);
        assertThat(cookies.get(0))
                .contains("sessionId=")
                .contains("Path=/")
                .contains("Max-Age=0")
                .contains("HttpOnly")
                .contains("SameSite=Lax");
        assertThat(cookies.get(1))
                .contains("refreshToken=")
                .contains("Path=/")
                .contains("Max-Age=0")
                .contains("HttpOnly")
                .contains("SameSite=Lax");
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
        assertThat(cookies).hasSize(2);
        assertThat(cookies.get(0))
                .contains("sessionId=session-id")
                .contains("Max-Age=1209600")
                .contains("HttpOnly");
        assertThat(cookies.get(1))
                .contains("refreshToken=new-refresh-token")
                .doesNotContain("current-refresh-token")
                .contains("Max-Age=1209600")
                .contains("HttpOnly");
    }
}
