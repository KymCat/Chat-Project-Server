package com.project.ChatProject.controller;

import com.project.ChatProject.config.security.AuthCookieFactory;
import com.project.ChatProject.dto.enums.ReauthenticationPurpose;
import com.project.ChatProject.dto.request.*;
import com.project.ChatProject.dto.response.ApiResponse;
import com.project.ChatProject.dto.response.MemberProfileResponse;
import com.project.ChatProject.dto.response.MemberPublicProfileResponse;
import com.project.ChatProject.dto.response.SignupResponse;
import com.project.ChatProject.dto.result.ProfileImageResult;
import com.project.ChatProject.dto.websocket.AuthWebSocketEvent;
import com.project.ChatProject.jwt.AccessTokenClaims;
import com.project.ChatProject.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

@RestController
@RequestMapping("/member")
@RequiredArgsConstructor
public class MemberController {

    private static final String REAUTHENTICATION_TOKEN_HEADER = "X-Reauthentication-Token";

    private final MemberService memberService;
    private final SimpMessagingTemplate messagingTemplate;
    private final AuthCookieFactory authCookieFactory;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MemberProfileResponse>> getProfile(
            @AuthenticationPrincipal AccessTokenClaims claims
    ) {
        MemberProfileResponse response =
                memberService.getProfile(claims.memberId());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{memberId}/profile")
    public ResponseEntity<ApiResponse<MemberPublicProfileResponse>> getPublicProfile(
            @PathVariable(name = "memberId") Long targetMemberId,
            @AuthenticationPrincipal AccessTokenClaims claims
    ) {
        Long myMemberId = claims.memberId();
        MemberPublicProfileResponse response =
                memberService.getPublicProfile(myMemberId, targetMemberId);

        return ResponseEntity
                .ok(ApiResponse.success(response));
    }

    @PatchMapping("/me/nickname")
    public ResponseEntity<ApiResponse<Void>> editNickname(
            @RequestBody @Valid UpdateNicknameRequest request,
            @AuthenticationPrincipal AccessTokenClaims claims
    ) {
        Long memberId = claims.memberId();
        memberService.editNickname(
                memberId,
                request.newNickname()
        );

        return ResponseEntity
                .ok(ApiResponse.success(null));
    }

    @GetMapping("/{memberId}/profile-image")
    public ResponseEntity<Resource> getProfileImage(
            @PathVariable Long memberId
    ) {
        ProfileImageResult result =
                memberService.getProfileImage(memberId);

        return ResponseEntity
                .ok()
                .cacheControl(
                        CacheControl
                                .maxAge(
                                        Duration.ofHours(1)
                                )
                                .cachePrivate()
                )
                .contentType(
                        MediaType.parseMediaType(
                                result.contentType()
                        )
                )
                .contentLength(
                        result.sizeBytes()
                )
                .body(
                        result.resource()
                );
    }

    @PutMapping("/me/profile-image/social")
    public ResponseEntity<ApiResponse<Void>> applySocialProfileImage(
            @Valid @RequestBody SocialProfileProviderRequest request,
            @AuthenticationPrincipal AccessTokenClaims claims
    ) {
        Long memberId = claims.memberId();
        memberService.applySocialProfileImage(memberId, request);

        return ResponseEntity
                .ok(ApiResponse.success(null));
    }

    @PutMapping(
            value = "/me/profile-image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ApiResponse<Void>> profileUpload(
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal AccessTokenClaims claims
    ) {
        Long memberId = claims.memberId();
        memberService.profileUpload(memberId, file);

        return ResponseEntity
                .ok(ApiResponse.success(null));
    }

    @DeleteMapping("/me/profile-image")
    public ResponseEntity<ApiResponse<Void>> deleteProfileImage(
            @AuthenticationPrincipal AccessTokenClaims claims
    ) {
        Long memberId = claims.memberId();
        memberService.deleteProfileImage(memberId);

        return ResponseEntity
                .ok(ApiResponse.success(null));
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(
            @RequestBody @Valid SignupRequest request)
    {
        SignupResponse response = memberService.signup(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PatchMapping("/me/status-message")
    public ResponseEntity<ApiResponse<Void>> updateStatusMessage(
            @RequestBody @Valid UpdateStatusMessage statusMessage,
            @AuthenticationPrincipal AccessTokenClaims claims
    ) {
        Long memberId = claims.memberId();
        memberService.updateStatusMessage(
                memberId,
                statusMessage.newStatusMessage()
        );

        return ResponseEntity
                .ok(ApiResponse.success(null));
    }

    @PatchMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @RequestHeader(REAUTHENTICATION_TOKEN_HEADER) String reauthenticationToken,
            @RequestBody @Valid ChangePasswordRequest request,
            @AuthenticationPrincipal AccessTokenClaims claims
    ) {
        Long memberId = claims.memberId();
        String sessionId = claims.sessionId();

        memberService.changePassword(
                memberId,
                sessionId,
                reauthenticationToken,
                ReauthenticationPurpose.CHANGE_PASSWORD,
                request.newPassword()
        );

        messagingTemplate.convertAndSendToUser(
                memberId.toString(),
                "/queue/auth",
                AuthWebSocketEvent.forceLogout()
        );

        String delSessionIdCookie = authCookieFactory.deleteSessionIdCookie();
        String delRefreshTokenCookie = authCookieFactory.deleteRefreshTokenCookie();

        return ResponseEntity
                .ok()
                .header(HttpHeaders.SET_COOKIE, delSessionIdCookie)
                .header(HttpHeaders.SET_COOKIE, delRefreshTokenCookie)
                .body(ApiResponse.success(null));
    }

    @PatchMapping("/me/withdraw")
    public ResponseEntity<ApiResponse<Void>> withdraw(
            @RequestHeader(REAUTHENTICATION_TOKEN_HEADER) String reauthenticationToken,
            @AuthenticationPrincipal AccessTokenClaims claims
    ) {
        Long memberId = claims.memberId();
        String sessionId = claims.sessionId();

        memberService.withdraw(
                memberId,
                sessionId,
                reauthenticationToken
        );

        messagingTemplate.convertAndSendToUser(
                memberId.toString(),
                "/queue/auth",
                AuthWebSocketEvent.forceLogout()
        );

        String delSessionIdCookie = authCookieFactory.deleteSessionIdCookie();
        String delRefreshTokenCookie = authCookieFactory.deleteRefreshTokenCookie();

        return ResponseEntity
                .ok()
                .header(HttpHeaders.SET_COOKIE, delSessionIdCookie)
                .header(HttpHeaders.SET_COOKIE, delRefreshTokenCookie)
                .body(ApiResponse.success(null));
    }
}
