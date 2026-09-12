package com.project.ChatProject.controller;

import com.project.ChatProject.config.websocket.WebSocketMemberPrincipal;
import com.project.ChatProject.dto.request.ChatEnterRequest;
import com.project.ChatProject.dto.request.ChatMessageRequest;
import com.project.ChatProject.dto.response.ChatMessageResponse;
import com.project.ChatProject.service.ChatMessageService;
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
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private SimpMessagingTemplate template;

    @Mock
    private ChatMessageService chatMessageService;

    private ChatController chatController;

    @BeforeEach
    void setUp() {
        chatController = new ChatController(
                template,
                chatMessageService
        );
    }

    @Test
    void enterCreatesAuthenticatedMemberResponseAndBroadcastsEntryMessage() {
        ChatEnterRequest request = new ChatEnterRequest(10L);
        Authentication authentication = authentication(
                1L,
                "홍길동"
        );
        ChatMessageResponse expectedResponse =
                new ChatMessageResponse(
                        "홍길동님이 입장하였습니다.",
                        1L,
                        "홍길동",
                        "ENTER",
                        10L
                );
        when(chatMessageService.enter(
                1L,
                "홍길동",
                10L,
                "ENTER"
        )).thenReturn(expectedResponse);

        ChatMessageResponse result = chatController.enter(
                request,
                authentication
        );

        assertThat(result).isEqualTo(expectedResponse);
        verify(chatMessageService).enter(
                1L,
                "홍길동",
                10L,
                "ENTER"
        );
        verify(template).convertAndSend(
                "/sub/enter/10",
                result
        );
    }

    @Test
    void sendCreatesAuthenticatedMemberResponseAndBroadcastsChatMessage() {
        ChatMessageRequest request = new ChatMessageRequest(
                10L,
                "안녕하세요"
        );
        Authentication authentication = authentication(
                1L,
                "홍길동"
        );
        ChatMessageResponse expectedResponse =
                new ChatMessageResponse(
                        "안녕하세요",
                        1L,
                        "홍길동",
                        "CHAT",
                        10L
                );
        when(chatMessageService.save(
                1L,
                request,
                "CHAT"
        )).thenReturn(expectedResponse);

        ChatMessageResponse result = chatController.send(
                request,
                authentication
        );

        assertThat(result).isEqualTo(expectedResponse);
        verify(chatMessageService).save(
                1L,
                request,
                "CHAT"
        );
        verify(template).convertAndSend(
                "/sub/msg/10",
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
