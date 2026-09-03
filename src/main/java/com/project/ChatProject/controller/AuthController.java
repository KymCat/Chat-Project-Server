package com.project.ChatProject.controller;

import com.project.ChatProject.dto.request.LoginRequest;
import com.project.ChatProject.dto.response.ApiResponse;
import com.project.ChatProject.dto.response.TokenResponse;
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
        TokenResponse response = authService.login(request);
        String accessToken = response.accessToken();
        String sessionIdCookie
                = setCookie(SESSION_ID_COOKIE_NAME, response.sessionId());
        String refreshTokenCookie
                = setCookie(REFRESH_TOKEN_COOKIE_NAME, response.refreshToken());


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
                setCookie(SESSION_ID_COOKIE_NAME, DEL_COOKIE_STR);

        String delRefreshTokenCookie =
            setCookie(REFRESH_TOKEN_COOKIE_NAME, DEL_COOKIE_STR);

        return ResponseEntity
                .ok()
                .header(HttpHeaders.SET_COOKIE, delSessionIdCookie)
                .header(HttpHeaders.SET_COOKIE, delRefreshTokenCookie)
                .body(ApiResponse.success(null));
    }

    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<String>> reissue(
            @CookieValue(name = SESSION_ID_COOKIE_NAME) String sessionId,
            @CookieValue(name = REFRESH_TOKEN_COOKIE_NAME) String refreshToken
    )
    {
        TokenResponse response = authService.reissue(sessionId, refreshToken);
        String accessToken = response.accessToken();
        String sessionIdCookie
                = setCookie(SESSION_ID_COOKIE_NAME, response.sessionId());
        String refreshTokenCookie
                = setCookie(REFRESH_TOKEN_COOKIE_NAME, response.refreshToken());

        return ResponseEntity
                .ok()
                .header(HttpHeaders.SET_COOKIE, sessionIdCookie)
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie)
                .body(ApiResponse.success(accessToken));
    }

    private String setCookie(
            String cookieName,
            String value
    )
    {
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
