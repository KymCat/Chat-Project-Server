package com.project.ChatProject.controller;

import com.project.ChatProject.config.websocket.WebSocketMemberPrincipal;
import com.project.ChatProject.dto.request.ChatMessageRequest;
import com.project.ChatProject.dto.response.ChatMessageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private SimpMessagingTemplate template;

    private ChatController chatController;

    @BeforeEach
    void setUp() {
        chatController = new ChatController(template);
    }

    @Test
    void enterCreatesAuthenticatedMemberResponseAndBroadcastsEntryMessage() {
        ChatMessageRequest request = new ChatMessageRequest(
                "",
                "room-1"
        );
        Authentication authentication = authentication(
                1L,
                "홍길동"
        );

        ChatMessageResponse result = chatController.enter(
                request,
                authentication
        );

        assertThat(result).isEqualTo(
                new ChatMessageResponse(
                        "홍길동님이 입장하였습니다.",
                        1L,
                        "홍길동",
                        "ENTER",
                        "room-1"
                )
        );
        verify(template).convertAndSend(
                "/sub/enter/room-1",
                result
        );
    }

    @Test
    void sendCreatesAuthenticatedMemberResponseAndBroadcastsChatMessage() {
        ChatMessageRequest request = new ChatMessageRequest(
                "안녕하세요",
                "room-1"
        );
        Authentication authentication = authentication(
                1L,
                "홍길동"
        );

        ChatMessageResponse result = chatController.send(
                request,
                authentication
        );

        assertThat(result).isEqualTo(
                new ChatMessageResponse(
                        "안녕하세요",
                        1L,
                        "홍길동",
                        "CHAT",
                        "room-1"
                )
        );
        verify(template).convertAndSend(
                "/sub/msg/room-1",
                result
        );
    }

    private Authentication authentication(
            Long memberId,
            String nickname
    ) {
        WebSocketMemberPrincipal principal =
                new WebSocketMemberPrincipal(
                memberId,
                        nickname
                );

        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of()
        );
    }
}
