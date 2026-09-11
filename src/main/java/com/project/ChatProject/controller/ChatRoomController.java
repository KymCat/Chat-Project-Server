package com.project.ChatProject.controller;

import com.project.ChatProject.dto.request.ChatRoomCreateRequest;
import com.project.ChatProject.dto.response.ApiResponse;
import com.project.ChatProject.dto.response.ChatRoomCreateResponse;
import com.project.ChatProject.dto.response.ChatRoomResponse;
import com.project.ChatProject.dto.response.GroupChatRoomResponse;
import com.project.ChatProject.jwt.AccessTokenClaims;
import com.project.ChatProject.service.ChatRoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/chat-rooms")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    @PostMapping
    public ResponseEntity<ApiResponse<ChatRoomCreateResponse>> create(
            @RequestBody @Valid ChatRoomCreateRequest request,
            @AuthenticationPrincipal AccessTokenClaims claims
    )
    {
        Long memberId = claims.memberId();
        ChatRoomCreateResponse response
                = chatRoomService.create(memberId, request.name());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ChatRoomResponse>>> getChatRooms(
            @AuthenticationPrincipal AccessTokenClaims claims
    ) {
        Long memberId = claims.memberId();
        List<ChatRoomResponse> response
                = chatRoomService.getChatRooms(memberId);

        return ResponseEntity
                .ok()
                .body(ApiResponse.success(response));
    }

    @GetMapping("/available")
    public ResponseEntity<ApiResponse<List<GroupChatRoomResponse>>> getGroupChatRooms(
            @AuthenticationPrincipal AccessTokenClaims claims
    )
    {
        Long memberId = claims.memberId();
        List<GroupChatRoomResponse> response
                = chatRoomService.getGroupChatRooms(memberId);

        return ResponseEntity
                .ok()
                .body(ApiResponse.success(response));
    }
}
