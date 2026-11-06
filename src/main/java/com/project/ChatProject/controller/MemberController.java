package com.project.ChatProject.controller;

import com.project.ChatProject.dto.request.SignupRequest;
import com.project.ChatProject.dto.request.SocialProfileProviderRequest;
import com.project.ChatProject.dto.request.UpdateStatusMessage;
import com.project.ChatProject.dto.response.ApiResponse;
import com.project.ChatProject.dto.response.MemberProfileResponse;
import com.project.ChatProject.dto.response.MemberPublicProfileResponse;
import com.project.ChatProject.dto.response.SignupResponse;
import com.project.ChatProject.dto.result.ProfileImageResult;
import com.project.ChatProject.jwt.AccessTokenClaims;
import com.project.ChatProject.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

@RestController
@RequestMapping("/member")
@RequiredArgsConstructor
public class MemberController {
    private final MemberService memberService;

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
}
