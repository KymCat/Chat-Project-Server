package com.project.ChatProject.controller;

import com.project.ChatProject.dto.request.FriendRequest;
import com.project.ChatProject.dto.response.ApiResponse;
import com.project.ChatProject.jwt.AccessTokenClaims;
import com.project.ChatProject.service.FriendshipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
