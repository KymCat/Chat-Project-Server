package com.project.ChatProject.controller;

import com.project.ChatProject.dto.event.MemberEvent;
import com.project.ChatProject.dto.response.ApiResponse;
import com.project.ChatProject.dto.response.BlockedMemberResponse;
import com.project.ChatProject.jwt.AccessTokenClaims;
import com.project.ChatProject.publisher.MemberEventPublisher;
import com.project.ChatProject.service.MemberBlockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/member")
public class MemberBlockController {

    private final MemberBlockService memberBlockService;
    private final MemberEventPublisher memberEventPublisher;

    @PostMapping("/blocks/{targetMemberId}")
    public ResponseEntity<ApiResponse<Void>> block(
            @PathVariable Long targetMemberId,
            @AuthenticationPrincipal AccessTokenClaims claims
    ) {
        Long blockerId = claims.memberId();

        MemberEvent event = memberBlockService.block(
                blockerId,
                targetMemberId
        );

        memberEventPublisher.publishHiddenEvent(
                event,
                targetMemberId
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(null));
    }

    @DeleteMapping("/blocks/{targetMemberId}")
    public ResponseEntity<ApiResponse<Void>> unblock(
            @PathVariable Long targetMemberId,
            @AuthenticationPrincipal AccessTokenClaims claims
    ) {
        Long blockerId = claims.memberId();

        MemberEvent event = memberBlockService.unblock(
                blockerId,
                targetMemberId
        );

        memberEventPublisher.publishHiddenEvent(
                event,
                targetMemberId
        );

        return ResponseEntity
                .ok(ApiResponse.success(null));
    }

    @GetMapping("/me/blocks")
    public ResponseEntity<ApiResponse<List<BlockedMemberResponse>>> getBlockedMembers(
            @AuthenticationPrincipal AccessTokenClaims claims
    ) {
        Long blockerId = claims.memberId();

        List<BlockedMemberResponse> response =
                memberBlockService.getBlockedMembers(blockerId);

        return ResponseEntity
                .ok(ApiResponse.success(response));
    }
}
