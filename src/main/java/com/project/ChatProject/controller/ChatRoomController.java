package com.project.ChatProject.controller;

import com.project.ChatProject.dto.request.ChatRoomCreateRequest;
import com.project.ChatProject.dto.response.*;
import com.project.ChatProject.jwt.AccessTokenClaims;
import com.project.ChatProject.service.ChatRoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/chat-rooms")
@RequiredArgsConstructor
public class ChatRoomController {

    private final SimpMessagingTemplate template;
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

    @PostMapping("/{roomId}/members")
    public ResponseEntity<ApiResponse<GroupChatRoomResponse>> join(
            @AuthenticationPrincipal AccessTokenClaims claims,
            @PathVariable Long roomId
    )
    {
        Long memberId = claims.memberId();
        ChatRoomJoinResponse response =
                chatRoomService.join(memberId, roomId);

        template.convertAndSend(
                "/sub/msg/" + roomId,
                response.chatMessage()
        );

        return ResponseEntity
                .ok()
                .body(ApiResponse.success(response.chatRoom()));
    }
}
