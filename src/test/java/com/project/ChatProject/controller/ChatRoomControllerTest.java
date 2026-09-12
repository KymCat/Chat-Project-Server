package com.project.ChatProject.controller;

import com.project.ChatProject.dto.response.ChatMessageResponse;
import com.project.ChatProject.dto.response.ChatRoomCreateResponse;
import com.project.ChatProject.dto.response.ChatRoomJoinResponse;
import com.project.ChatProject.dto.response.GroupChatRoomResponse;
import com.project.ChatProject.entity.enums.ChatMessageType;
import com.project.ChatProject.entity.enums.ChatRoomType;
import com.project.ChatProject.jwt.AccessTokenClaims;
import com.project.ChatProject.service.ChatRoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ChatRoomControllerTest {

    @Mock
    private ChatRoomService chatRoomService;

    @Mock
    private SimpMessagingTemplate template;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ChatRoomController controller =
                new ChatRoomController(template, chatRoomService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setCustomArgumentResolvers(
                        authenticationPrincipalResolver(claims())
                )
                .build();
    }

    @Test
    void createReturnsCreatedRoomAndDelegatesNormalizedName() throws Exception {
        ChatRoomCreateResponse response = new ChatRoomCreateResponse(
                10L,
                ChatRoomType.GROUP,
                "Backend"
        );
        when(chatRoomService.create(1L, "Backend"))
                .thenReturn(response);

        mockMvc.perform(post("/chat-rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  Backend  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.roomId").value(10L))
                .andExpect(jsonPath("$.data.type").value("GROUP"))
                .andExpect(jsonPath("$.data.name").value("Backend"))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist());

        verify(chatRoomService).create(1L, "Backend");
    }

    @Test
    void createRejectsBlankName() throws Exception {
        mockMvc.perform(post("/chat-rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());

        verify(chatRoomService, never()).create(1L, "");
    }

    @Test
    void createRejectsNameLongerThanThirtyCharactersAfterNormalization()
            throws Exception {
        String name = "a".repeat(31);

        mockMvc.perform(post("/chat-rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  " + name + "  \"}"))
                .andExpect(status().isBadRequest());

        verify(chatRoomService, never()).create(1L, name);
    }

    @Test
    void getAvailableGroupChatRoomsReturnsAuthenticatedMemberRooms()
            throws Exception {
        Instant lastMessageAt = Instant.parse("2026-09-11T01:00:00Z");
        when(chatRoomService.getGroupChatRooms(1L))
                .thenReturn(List.of(
                        new GroupChatRoomResponse(
                                10L,
                                "Backend",
                                lastMessageAt
                        ),
                        new GroupChatRoomResponse(
                                11L,
                                "Java",
                                null
                        )
                ));

        mockMvc.perform(get("/chat-rooms/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].roomId").value(10L))
                .andExpect(jsonPath("$.data[0].name").value("Backend"))
                .andExpect(jsonPath("$.data[0].lastMessageAt").exists())
                .andExpect(jsonPath("$.data[1].roomId").value(11L))
                .andExpect(jsonPath("$.data[1].name").value("Java"))
                .andExpect(jsonPath("$.data[1].lastMessageAt").doesNotExist());

        verify(chatRoomService).getGroupChatRooms(1L);
    }

    @Test
    void joinReturnsRoomAndBroadcastsPersistedSystemMessage() throws Exception {
        Instant createdAt = Instant.parse("2026-09-12T06:00:00Z");
        GroupChatRoomResponse chatRoom =
                new GroupChatRoomResponse(10L, "Backend", createdAt);
        ChatMessageResponse chatMessage =
                new ChatMessageResponse(
                        100L,
                        10L,
                        null,
                        null,
                        ChatMessageType.SYSTEM,
                        "사용자님이 입장하였습니다.",
                        createdAt
                );
        when(chatRoomService.join(1L, 10L))
                .thenReturn(new ChatRoomJoinResponse(chatRoom, chatMessage));

        mockMvc.perform(post("/chat-rooms/10/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.roomId").value(10L))
                .andExpect(jsonPath("$.data.name").value("Backend"))
                .andExpect(jsonPath("$.data.lastMessageAt").exists());

        verify(chatRoomService).join(1L, 10L);
        verify(template).convertAndSend(
                "/sub/msg/10",
                chatMessage
        );
    }

    private HandlerMethodArgumentResolver authenticationPrincipalResolver(
            AccessTokenClaims claims
    ) {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(
                        AuthenticationPrincipal.class
                );
            }

            @Override
            public Object resolveArgument(
                    MethodParameter parameter,
                    ModelAndViewContainer mavContainer,
                    NativeWebRequest webRequest,
                    WebDataBinderFactory binderFactory
            ) {
                return claims;
            }
        };
    }

    private AccessTokenClaims claims() {
        return new AccessTokenClaims(
                1L,
                "token-id",
                "session-id",
                false,
                Instant.now(),
                Instant.now().plusSeconds(600)
        );
    }
}
