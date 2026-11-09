package com.project.ChatProject.controller;

import com.project.ChatProject.config.security.AuthCookieFactory;
import com.project.ChatProject.dto.enums.ReauthenticationPurpose;
import com.project.ChatProject.dto.request.EmailVerificationConfirmRequest;
import com.project.ChatProject.dto.request.LoginRequest;
import com.project.ChatProject.dto.request.OAuthLoginCodeExchangeRequest;
import com.project.ChatProject.dto.request.ReauthenticationPasswordRequest;
import com.project.ChatProject.dto.response.ApiResponse;
import com.project.ChatProject.dto.response.ReauthenticationMethodResponse;
import com.project.ChatProject.dto.response.TokenResponse;
import com.project.ChatProject.jwt.AccessTokenClaims;
import com.project.ChatProject.service.AuthService;
import com.project.ChatProject.service.EmailVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final AuthCookieFactory authCookieFactory;

    private static final String SESSION_ID_COOKIE_NAME = "sessionId";
    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<String>> login(
            @RequestBody @Valid LoginRequest request
    ) {
        TokenResponse response = authService.login(request);
        String accessToken = response.accessToken();
        String sessionIdCookie =
                authCookieFactory.createSessionIdCookie(response.sessionId());
        String refreshTokenCookie =
                authCookieFactory.createRefreshTokenCookie(response.refreshToken());


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

        String delSessionIdCookie = authCookieFactory.deleteSessionIdCookie();
        String delRefreshTokenCookie = authCookieFactory.deleteRefreshTokenCookie();

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
        String sessionIdCookie =
                authCookieFactory.createSessionIdCookie(response.sessionId());
        String refreshTokenCookie =
                authCookieFactory.createRefreshTokenCookie(response.refreshToken());

        return ResponseEntity
                .ok()
                .header(HttpHeaders.SET_COOKIE, sessionIdCookie)
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie)
                .body(ApiResponse.success(accessToken));
    }

    @PostMapping("/email-verifications")
    public ResponseEntity<ApiResponse<Void>> request(
            @AuthenticationPrincipal AccessTokenClaims claims
    )
    {
        Long memberId = claims.memberId();
        emailVerificationService.request(memberId);

        return ResponseEntity
                .ok()
                .body(ApiResponse.success(null));
    }

    @PostMapping("/email-verifications/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmEmailVerification(
            @AuthenticationPrincipal AccessTokenClaims claims,
            @RequestBody @Valid EmailVerificationConfirmRequest request
    )
    {
        Long memberId = claims.memberId();
        String code = request.code();
        emailVerificationService.confirm(memberId, code);

        return ResponseEntity
                .ok()
                .body(ApiResponse.success(null));
    }

    @PostMapping("/oauth/exchange")
    public ResponseEntity<ApiResponse<String>> exchangeOAuthLoginCode(
            @CookieValue(name = SESSION_ID_COOKIE_NAME) String sessionId,
            @RequestBody @Valid OAuthLoginCodeExchangeRequest request
    ) {
        String code = request.code();
        String accessToken = authService.exchangeOAuthLoginCode(code, sessionId);

        return ResponseEntity
                .ok(ApiResponse.success(accessToken));
    }

    @PostMapping("/logout/all")
    public ResponseEntity<ApiResponse<Void>> logoutAllSessions(
            @AuthenticationPrincipal AccessTokenClaims claims
    ) {
        Long memberId = claims.memberId();
        authService.logoutAllSessions(memberId);

        String delSessionIdCookie = authCookieFactory.deleteSessionIdCookie();
        String delRefreshTokenCookie = authCookieFactory.deleteRefreshTokenCookie();

        return ResponseEntity
                .ok()
                .header(HttpHeaders.SET_COOKIE, delSessionIdCookie)
                .header(HttpHeaders.SET_COOKIE, delRefreshTokenCookie)
                .body(ApiResponse.success(null));
    }

    @GetMapping("/reauthentication-method")
    public ResponseEntity<ApiResponse<ReauthenticationMethodResponse>> getReauthenticationMethod(
            @AuthenticationPrincipal AccessTokenClaims claims
    ) {
        Long memberId = claims.memberId();
        ReauthenticationMethodResponse response =
                authService.getReauthenticationMethod(memberId);

        return ResponseEntity
                .ok(ApiResponse.success(response));
    }

    @PostMapping("/reauthentication/password")
    public ResponseEntity<ApiResponse<String>> reauthenticateWithPassword(
            @RequestBody @Valid ReauthenticationPasswordRequest request,
            @AuthenticationPrincipal AccessTokenClaims claims
    ) {
        Long memberId = claims.memberId();
        String sessionId = claims.sessionId();

        String response = authService.reauthenticateWithPassword(
                memberId,
                sessionId,
                request.password(),
                request.purpose()
        );

        return ResponseEntity
                .ok(ApiResponse.success(response));
    }
}
