package com.project.ChatProject.controller;

import com.project.ChatProject.dto.ChatMessageDto;
import com.project.ChatProject.jwt.AccessTokenClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
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
    void enterOverwritesSenderAndBroadcastsEntryMessage() {
        ChatMessageDto message = new ChatMessageDto(
                "",
                "forged-sender",
                "ENTER",
                "room-1"
        );
        Authentication authentication = authentication(1L);

        ChatMessageDto result = chatController.enter(
                message,
                authentication
        );

        assertThat(result).isSameAs(message);
        assertThat(result.getSender()).isEqualTo("member-1");
        assertThat(result.getContent())
                .isEqualTo("member-1님이 입장하였습니다.");
        verify(template).convertAndSend(
                "/sub/enter/room-1",
                message
        );
    }

    @Test
    void sendOverwritesSenderAndBroadcastsChatMessage() {
        ChatMessageDto message = new ChatMessageDto(
                "안녕하세요",
                "forged-sender",
                "CHAT",
                "room-1"
        );
        Authentication authentication = authentication(1L);

        ChatMessageDto result = chatController.send(
                message,
                authentication
        );

        assertThat(result).isSameAs(message);
        assertThat(result.getSender()).isEqualTo("member-1");
        assertThat(result.getContent()).isEqualTo("안녕하세요");
        verify(template).convertAndSend(
                "/sub/msg/room-1",
                message
        );
    }

    private Authentication authentication(Long memberId) {
        AccessTokenClaims claims = new AccessTokenClaims(
                memberId,
                "token-id",
                "session-id",
                true,
                Instant.now(),
                Instant.now().plusSeconds(600)
        );

        return new UsernamePasswordAuthenticationToken(
                claims,
                null,
                List.of()
        );
    }
}
