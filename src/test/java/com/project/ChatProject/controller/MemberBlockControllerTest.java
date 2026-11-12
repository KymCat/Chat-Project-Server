package com.project.ChatProject.controller;

import com.project.ChatProject.dto.event.MemberEvent;
import com.project.ChatProject.dto.response.ApiResponse;
import com.project.ChatProject.dto.response.BlockedMemberResponse;
import com.project.ChatProject.entity.enums.MemberStatus;
import com.project.ChatProject.jwt.AccessTokenClaims;
import com.project.ChatProject.publisher.MemberEventPublisher;
import com.project.ChatProject.service.MemberBlockService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberBlockControllerTest {

    @Mock
    private MemberBlockService memberBlockService;

    @Mock
    private MemberEventPublisher memberEventPublisher;

    @InjectMocks
    private MemberBlockController memberBlockController;

    @Test
    void blockReturnsCreatedAndPublishesVisibilityEvent() {
        MemberEvent event = MemberEvent.hidden(1L);
        when(memberBlockService.block(1L, 2L)).thenReturn(event);

        ResponseEntity<ApiResponse<Void>> response =
                memberBlockController.block(2L, claims());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        verify(memberBlockService).block(1L, 2L);
        verify(memberEventPublisher).publishHiddenEvent(event, 2L);
    }

    @Test
    void unblockReturnsOkAndPublishesVisibilityEvent() {
        // 추가: B에게 전달되는 payload에는 다시 표시할 A의 ID가 들어간다.
        MemberEvent event = MemberEvent.unhidden(1L);
        when(memberBlockService.unblock(1L, 2L)).thenReturn(event);

        ResponseEntity<ApiResponse<Void>> response =
                memberBlockController.unblock(2L, claims());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        verify(memberBlockService).unblock(1L, 2L);
        verify(memberEventPublisher).publishHiddenEvent(event, 2L);
    }

    @Test
    void getBlockedMembersReturnsAuthenticatedMemberBlocks() {
        List<BlockedMemberResponse> blockedMembers = List.of(
                new BlockedMemberResponse(2L, "차단회원", MemberStatus.ACTIVE)
        );
        when(memberBlockService.getBlockedMembers(1L)).thenReturn(blockedMembers);

        ResponseEntity<ApiResponse<List<BlockedMemberResponse>>> response =
                memberBlockController.getBlockedMembers(claims());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isEqualTo(blockedMembers);
        verify(memberBlockService).getBlockedMembers(1L);
    }

    private AccessTokenClaims claims() {
        return new AccessTokenClaims(
                1L,
                "token-id",
                "session-id",
                true,
                Instant.now(),
                Instant.now().plusSeconds(600)
        );
    }
}
