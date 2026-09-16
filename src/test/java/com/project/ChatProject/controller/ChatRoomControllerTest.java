package com.project.ChatProject.controller;

import com.project.ChatProject.dto.response.ChatMessageResponse;
import com.project.ChatProject.dto.response.ChatRoomCreateResponse;
import com.project.ChatProject.dto.response.ChatRoomJoinResponse;
import com.project.ChatProject.dto.response.ChatRoomMemberResponse;
import com.project.ChatProject.dto.response.CursorPageResponse;
import com.project.ChatProject.dto.response.GroupChatRoomResponse;
import com.project.ChatProject.entity.enums.ChatMessageType;
import com.project.ChatProject.entity.enums.ChatRoomType;
import com.project.ChatProject.entity.enums.ChatRoomMemberRole;
import com.project.ChatProject.jwt.AccessTokenClaims;
import com.project.ChatProject.exception.GlobalExceptionHandler;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
                .setControllerAdvice(new GlobalExceptionHandler())
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
                        createdAt,
                        false
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

    @Test
    void getMessagesReturnsCursorPage() throws Exception {
        Instant createdAt = Instant.parse("2026-09-14T06:00:00Z");
        ChatMessageResponse message = new ChatMessageResponse(
                90L,
                10L,
                1L,
                "사용자",
                ChatMessageType.TEXT,
                "안녕하세요",
                createdAt,
                false
        );
        CursorPageResponse<ChatMessageResponse> response =
                CursorPageResponse.of(List.of(message), 90L, true);
        when(chatRoomService.getMessages(10L, 100L, 1L, 20))
                .thenReturn(response);

        mockMvc.perform(get("/chat-rooms/10/messages")
                        .param("beforeMessageId", "100")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].messageId").value(90L))
                .andExpect(jsonPath("$.data.content[0].content").value("안녕하세요"))
                .andExpect(jsonPath("$.data.nextCursor").value(90L))
                .andExpect(jsonPath("$.data.hasNext").value(true));

        verify(chatRoomService).getMessages(10L, 100L, 1L, 20);
    }

    @Test
    void getMessagesRejectsSizeOutsideAllowedRange() throws Exception {
        mockMvc.perform(get("/chat-rooms/10/messages").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/chat-rooms/10/messages").param("size", "61"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(chatRoomService, never())
                .getMessages(any(Long.class), any(), any(Long.class), any(Integer.class));
    }

    @Test
    void leaveReturnsSuccessAndBroadcastsPersistedSystemMessage() throws Exception {
        Instant createdAt = Instant.parse("2026-09-14T09:00:00Z");
        ChatMessageResponse leaveMessage = new ChatMessageResponse(
                110L,
                10L,
                null,
                null,
                ChatMessageType.SYSTEM,
                "사용자님이 퇴장하였습니다.",
                createdAt,
                false
        );
        when(chatRoomService.leave(10L, 1L)).thenReturn(leaveMessage);

        mockMvc.perform(delete("/chat-rooms/10/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(chatRoomService).leave(10L, 1L);
        verify(template).convertAndSend(
                "/sub/msg/10",
                leaveMessage
        );
    }

    @Test
    void getMembersReturnsChatRoomMemberList() throws Exception {
        Instant joinedAt = Instant.parse("2026-09-14T09:00:00Z");
        List<ChatRoomMemberResponse> response = List.of(
                new ChatRoomMemberResponse(
                        1L,
                        "사용자",
                        ChatRoomMemberRole.OWNER,
                        joinedAt
                ),
                new ChatRoomMemberResponse(
                        2L,
                        "비활성화된 회원",
                        ChatRoomMemberRole.MEMBER,
                        joinedAt.plusSeconds(60)
                )
        );
        when(chatRoomService.getMembers(10L, 1L)).thenReturn(response);

        mockMvc.perform(get("/chat-rooms/10/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].memberId").value(1L))
                .andExpect(jsonPath("$.data[0].displayName").value("사용자"))
                .andExpect(jsonPath("$.data[0].role").value("OWNER"))
                .andExpect(jsonPath("$.data[0].joinedAt").exists())
                .andExpect(jsonPath("$.data[1].displayName")
                        .value("비활성화된 회원"));

        verify(chatRoomService).getMembers(10L, 1L);
    }

    @Test
    void transferOwnershipReturnsSuccess() throws Exception {
        Instant createdAt = Instant.parse("2026-09-15T10:00:00Z");
        ChatMessageResponse ownerTransferMessage = new ChatMessageResponse(
                120L,
                10L,
                null,
                null,
                ChatMessageType.SYSTEM,
                "새방장님이 방장으로 위임되셨습니다.",
                createdAt,
                false
        );
        when(chatRoomService.transferOwnership(10L, 1L, 2L))
                .thenReturn(ownerTransferMessage);

        mockMvc.perform(patch("/chat-rooms/10/owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newOwnerMemberId\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(chatRoomService).transferOwnership(10L, 1L, 2L);
        verify(template).convertAndSend(
                "/sub/msg/10",
                ownerTransferMessage
        );
    }

    @Test
    void transferOwnershipRejectsMissingTargetMemberId() throws Exception {
        mockMvc.perform(patch("/chat-rooms/10/owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(chatRoomService, never())
                .transferOwnership(any(Long.class), any(Long.class), any(Long.class));
        verify(template, never())
                .convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void updateReadPositionReturnsSuccess() throws Exception {
        mockMvc.perform(patch("/chat-rooms/10/read-position")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastReadMessageId\":120}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(chatRoomService).updateReadPosition(10L, 1L, 120L);
    }

    @Test
    void updateReadPositionRejectsMissingMessageId() throws Exception {
        mockMvc.perform(patch("/chat-rooms/10/read-position")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(chatRoomService, never())
                .updateReadPosition(any(Long.class), any(Long.class), any(Long.class));
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
