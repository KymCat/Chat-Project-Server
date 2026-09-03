package com.project.ChatProject.controller;

import com.project.ChatProject.dto.request.LoginRequest;
import com.project.ChatProject.dto.response.ApiResponse;
import com.project.ChatProject.dto.response.LoginResponse;
import com.project.ChatProject.jwt.refresh.RefreshTokenProperties;
import com.project.ChatProject.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final RefreshTokenProperties refreshTokenProperties;

    private static final String SESSION_ID_COOKIE_NAME = "sessionId";
    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";

    @Value("${cookie.secure}")
    private boolean secure;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<String>> login(
            @RequestBody @Valid LoginRequest request
    ) {
        LoginResponse result = authService.login(request);
        String accessToken = result.accessToken();
        String sessionIdCookie = sessionIdCookie(result.sessionId()).toString();
        String refreshTokenCookie
                = refreshTokenCookie(result.refreshToken()).toString();


        return ResponseEntity
                .ok()
                .header(HttpHeaders.SET_COOKIE, sessionIdCookie)
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie)
                .body(ApiResponse.success(accessToken));
    }

    private ResponseCookie refreshTokenCookie(String refreshToken) {
        return ResponseCookie
                .from(
                        REFRESH_TOKEN_COOKIE_NAME,
                        refreshToken
                )
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(refreshTokenProperties.expiration())
                .sameSite("Lax")
                .build();
    }

    private ResponseCookie sessionIdCookie(String sessionId) {
        return ResponseCookie
                .from(
                        SESSION_ID_COOKIE_NAME,
                        sessionId
                )
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(refreshTokenProperties.expiration())
                .sameSite("Lax")
                .build();
    }
}
