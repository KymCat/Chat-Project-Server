package com.project.ChatProject.controller;

import com.project.ChatProject.dto.request.FriendRequest;
import com.project.ChatProject.dto.response.ApiResponse;
import com.project.ChatProject.dto.response.CursorPageResponse;
import com.project.ChatProject.dto.response.FriendRequestResponse;
import com.project.ChatProject.jwt.AccessTokenClaims;
import com.project.ChatProject.service.FriendshipService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/friends")
public class FriendshipController {

    private final FriendshipService friendshipService;

    @PostMapping("/requests")
    public ResponseEntity<ApiResponse<Void>> requestFriend(
            @Valid @RequestBody FriendRequest request,
            @AuthenticationPrincipal AccessTokenClaims claims
    ) {
        Long requesterId = claims.memberId();
        Long receiverId = request.targetMemberId();

        friendshipService.requestFriend(requesterId, receiverId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(null));
    }

    @GetMapping("/requests/sent")
    public ResponseEntity<ApiResponse<CursorPageResponse<FriendRequestResponse>>> requestedList(
            @RequestParam(defaultValue = "30") @Min(1) @Max(60) int size,
            @RequestParam(required = false) Long beforeFriendshipId,
            @AuthenticationPrincipal AccessTokenClaims claims
    ) {
        Long memberId = claims.memberId();
        CursorPageResponse<FriendRequestResponse> response =
                friendshipService.requestedList(memberId, beforeFriendshipId, size);

        return ResponseEntity
                .ok(ApiResponse.success(response));
    }

    @GetMapping("/requests/received")
    public ResponseEntity<ApiResponse<CursorPageResponse<FriendRequestResponse>>> receivedList(
            @RequestParam(defaultValue = "30") @Min(1) @Max(60) int size,
            @RequestParam(required = false) Long beforeFriendshipId,
            @AuthenticationPrincipal AccessTokenClaims claims
    ) {
        Long memberId = claims.memberId();
        CursorPageResponse<FriendRequestResponse> response =
                friendshipService.receivedList(memberId, beforeFriendshipId, size);

        return ResponseEntity
                .ok(ApiResponse.success(response));
    }

    @PostMapping("/requests/{requestId}/accept")
    public ResponseEntity<ApiResponse<Void>> acceptRequest(
            @PathVariable Long requestId,
            @AuthenticationPrincipal AccessTokenClaims claims
    ) {
        Long memberId = claims.memberId();
        friendshipService.acceptRequest(requestId, memberId);

        return ResponseEntity
                .ok(ApiResponse.success(null));
    }

    @DeleteMapping("/requests/{requestId}")
    public ResponseEntity<ApiResponse<Void>> deleteRequest(
            @PathVariable Long requestId,
            @AuthenticationPrincipal AccessTokenClaims claims
    ) {
        Long memberId = claims.memberId();
        friendshipService.deleteRequest(requestId, memberId);

        return ResponseEntity
                .ok(ApiResponse.success(null));
    }
}
