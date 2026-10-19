package com.project.ChatProject.controller;

import com.project.ChatProject.dto.request.SignupRequest;
import com.project.ChatProject.dto.response.ApiResponse;
import com.project.ChatProject.dto.response.MemberProfileResponse;
import com.project.ChatProject.dto.response.SignupResponse;
import com.project.ChatProject.jwt.AccessTokenClaims;
import com.project.ChatProject.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/member")
@RequiredArgsConstructor
public class MemberController {
    private final MemberService memberService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MemberProfileResponse>> getProfile(
            @AuthenticationPrincipal AccessTokenClaims claims
    ) {
        MemberProfileResponse response = memberService.getProfile(claims.memberId());

        return ResponseEntity.ok(ApiResponse.success(response));
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
}
