package com.project.ChatProject.controller;

import com.project.ChatProject.dto.response.CursorPageResponse;
import com.project.ChatProject.dto.response.FriendListResponse;
import com.project.ChatProject.dto.response.FriendRequestResponse;
import com.project.ChatProject.entity.enums.MemberStatus;
import com.project.ChatProject.exception.GlobalExceptionHandler;
import com.project.ChatProject.jwt.AccessTokenClaims;
import com.project.ChatProject.service.FriendshipService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FriendshipControllerTest {

    private static final Long MEMBER_ID = 1L;

    @Mock
    private FriendshipService friendshipService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        FriendshipController controller =
                new FriendshipController(friendshipService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        authenticationPrincipalResolver(claims())
                )
                .build();
    }

    @Test
    void requestedListReturnsSentRequestsWithCursor() throws Exception {
        FriendRequestResponse friendRequest = new FriendRequestResponse(
                90L,
                2L,
                "수신자",
                Instant.parse("2026-10-29T00:00:00Z")
        );
        CursorPageResponse<FriendRequestResponse> response =
                CursorPageResponse.of(
                        List.of(friendRequest),
                        90L,
                        true
                );
        when(friendshipService.requestedList(
                MEMBER_ID,
                100L,
                20
        )).thenReturn(response);

        mockMvc.perform(get("/friends/requests/sent")
                        .param("beforeFriendshipId", "100")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].requestId")
                        .value(90L))
                .andExpect(jsonPath("$.data.content[0].targetMemberId")
                        .value(2L))
                .andExpect(jsonPath("$.data.content[0].targetDisplayName")
                        .value("수신자"))
                .andExpect(jsonPath("$.data.nextCursor").value(90L))
                .andExpect(jsonPath("$.data.hasNext").value(true));

        verify(friendshipService).requestedList(
                MEMBER_ID,
                100L,
                20
        );
    }

    @Test
    void receivedListUsesDefaultSizeAndNullCursor() throws Exception {
        FriendRequestResponse friendRequest = new FriendRequestResponse(
                80L,
                3L,
                "요청자",
                Instant.parse("2026-10-29T00:00:00Z")
        );
        CursorPageResponse<FriendRequestResponse> response =
                CursorPageResponse.of(
                        List.of(friendRequest),
                        null,
                        false
                );
        when(friendshipService.receivedList(
                MEMBER_ID,
                null,
                30
        )).thenReturn(response);

        mockMvc.perform(get("/friends/requests/received"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].requestId")
                        .value(80L))
                .andExpect(jsonPath("$.data.content[0].targetMemberId")
                        .value(3L))
                .andExpect(jsonPath("$.data.content[0].targetDisplayName")
                        .value("요청자"))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist())
                .andExpect(jsonPath("$.data.hasNext").value(false));

        verify(friendshipService).receivedList(
                MEMBER_ID,
                null,
                30
        );
    }

    @Test
    void requestListsRejectSizeOutsideAllowedRange() throws Exception {
        mockMvc.perform(get("/friends/requests/sent")
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/friends/requests/received")
                        .param("size", "61"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(friendshipService);
    }

    @Test
    void getFriendListReturnsAuthenticatedMembersFriends() throws Exception {
        FriendListResponse friend = new FriendListResponse(
                2L,
                "친구",
                "상태메세지",
                MemberStatus.ACTIVE
        );
        when(friendshipService.getFriendList(MEMBER_ID))
                .thenReturn(List.of(friend));

        mockMvc.perform(get("/friends/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].memberId").value(2L))
                .andExpect(jsonPath("$.data[0].displayName")
                        .value("친구"))
                .andExpect(jsonPath("$.data[0].statusMessage")
                        .value("상태메세지"))
                .andExpect(jsonPath("$.data[0].status")
                        .value("ACTIVE"));

        verify(friendshipService).getFriendList(MEMBER_ID);
    }

    @Test
    void acceptRequestDelegatesAuthenticatedReceiver() throws Exception {
        mockMvc.perform(post("/friends/requests/100/accept"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(friendshipService).acceptRequest(
                100L,
                MEMBER_ID
        );
    }

    @Test
    void deleteRequestDelegatesAuthenticatedParticipant() throws Exception {
        mockMvc.perform(delete("/friends/requests/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(friendshipService).deleteRequest(
                100L,
                MEMBER_ID
        );
    }

    @Test
    void unfollowDelegatesAuthenticatedMemberAndTargetMember() throws Exception {
        mockMvc.perform(delete("/friends/unfollow/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(friendshipService).unfollow(
                MEMBER_ID,
                2L
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
                MEMBER_ID,
                "token-id",
                "session-id",
                false,
                Instant.now(),
                Instant.now().plusSeconds(600)
        );
    }
}
