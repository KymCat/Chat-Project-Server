package com.project.ChatProject.controller;

import com.project.ChatProject.dto.request.LoginRequest;
import com.project.ChatProject.dto.response.ApiResponse;
import com.project.ChatProject.dto.response.LoginResponse;
import com.project.ChatProject.jwt.AccessTokenClaims;
import com.project.ChatProject.jwt.refresh.RefreshTokenProperties;
import com.project.ChatProject.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final RefreshTokenProperties refreshTokenProperties;

    private static final String SESSION_ID_COOKIE_NAME = "sessionId";
    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
    private static final String DEL_COOKIE_STR = "";

    @Value("${cookie.secure}")
    private boolean secure;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<String>> login(
            @RequestBody @Valid LoginRequest request
    ) {
        LoginResponse result = authService.login(request);
        String accessToken = result.accessToken();
        String sessionIdCookie
                = setCookie(SESSION_ID_COOKIE_NAME, result.sessionId()).toString();
        String refreshTokenCookie
                = setCookie(REFRESH_TOKEN_COOKIE_NAME, result.refreshToken()).toString();


        return ResponseEntity
                .ok()
                .header(HttpHeaders.SET_COOKIE, sessionIdCookie)
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie)
                .body(ApiResponse.success(accessToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal AccessTokenClaims claims
    )
    {

        authService.logout(claims);

        String delSessionIdCookie =
                setCookie(SESSION_ID_COOKIE_NAME, DEL_COOKIE_STR).toString();

        String delRefreshTokenCookie =
            setCookie(REFRESH_TOKEN_COOKIE_NAME, DEL_COOKIE_STR).toString();

        return ResponseEntity
                .ok()
                .header(HttpHeaders.SET_COOKIE, delSessionIdCookie)
                .header(HttpHeaders.SET_COOKIE, delRefreshTokenCookie)
                .body(ApiResponse.success(null));
    }

    private ResponseCookie setCookie(
            String cookieName,
            String refreshToken
    )
    {
        Duration expiration = StringUtils.hasLength(refreshToken)
                ? refreshTokenProperties.expiration()
                : Duration.ZERO;

        return ResponseCookie
                .from(
                        cookieName,
                        refreshToken
                )
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(expiration)
                .sameSite("Lax")
                .build();
    }
}
