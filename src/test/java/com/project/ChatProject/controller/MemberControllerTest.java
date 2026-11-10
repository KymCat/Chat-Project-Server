package com.project.ChatProject.controller;

import com.project.ChatProject.config.security.AuthCookieFactory;
import com.project.ChatProject.dto.enums.ReauthenticationPurpose;
import com.project.ChatProject.dto.request.ChangePasswordRequest;
import com.project.ChatProject.dto.request.SocialProfileProviderRequest;
import com.project.ChatProject.dto.request.UpdateNicknameRequest;
import com.project.ChatProject.dto.request.UpdateStatusMessage;
import com.project.ChatProject.dto.result.ProfileImageResult;
import com.project.ChatProject.dto.response.ApiResponse;
import com.project.ChatProject.dto.response.MemberProfileResponse;
import com.project.ChatProject.dto.response.MemberPublicProfileResponse;
import com.project.ChatProject.dto.enums.ProfileRelationStatus;
import com.project.ChatProject.dto.websocket.AuthWebSocketEvent;
import com.project.ChatProject.entity.enums.MemberStatus;
import com.project.ChatProject.entity.enums.SocialProvider;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.jwt.AccessTokenClaims;
import com.project.ChatProject.service.MemberService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberControllerTest {

    @Mock
    private MemberService memberService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private AuthCookieFactory authCookieFactory;

    @InjectMocks
    private MemberController memberController;

    @Test
    void getProfileUsesAuthenticatedMemberId() {
        AccessTokenClaims claims = new AccessTokenClaims(
                2L,
                "token-id",
                "session-id",
                true,
                Instant.now(),
                Instant.now().plusSeconds(600)
        );
        MemberProfileResponse profile = new MemberProfileResponse(
                "베리타스",
                "veritas@example.com",
                "개발 공부 중",
                MemberStatus.ACTIVE,
                false
        );
        when(memberService.getProfile(2L)).thenReturn(profile);

        ResponseEntity<ApiResponse<MemberProfileResponse>> response =
                memberController.getProfile(claims);

        verify(memberService).getProfile(2L);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isEqualTo(profile);
    }

    @Test
    void getPublicProfileUsesAuthenticatedAndTargetMemberIds() {
        AccessTokenClaims claims = claims();
        MemberPublicProfileResponse profile =
                new MemberPublicProfileResponse(
                        3L,
                        "상대방",
                        "상태 메시지",
                        ProfileRelationStatus.NONE,
                        MemberStatus.ACTIVE
                );
        when(memberService.getPublicProfile(2L, 3L))
                .thenReturn(profile);

        ResponseEntity<ApiResponse<MemberPublicProfileResponse>> response =
                memberController.getPublicProfile(3L, claims);

        verify(memberService).getPublicProfile(2L, 3L);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isEqualTo(profile);
    }

    @Test
    void updateStatusMessageUsesAuthenticatedMemberId() {
        AccessTokenClaims claims = claims();
        UpdateStatusMessage request =
                new UpdateStatusMessage("개발 공부 중");

        ResponseEntity<ApiResponse<Void>> response =
                memberController.updateStatusMessage(request, claims);

        verify(memberService).updateStatusMessage(
                2L,
                "개발 공부 중"
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data()).isNull();
    }

    @Test
    void updateStatusMessageAllowsNullToClearStatusMessage() {
        AccessTokenClaims claims = claims();
        UpdateStatusMessage request = new UpdateStatusMessage(null);

        ResponseEntity<ApiResponse<Void>> response =
                memberController.updateStatusMessage(request, claims);

        verify(memberService).updateStatusMessage(2L, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data()).isNull();
    }

    @Test
    void editNicknameUsesAuthenticatedMemberId() {
        AccessTokenClaims claims = claims();
        UpdateNicknameRequest request =
                new UpdateNicknameRequest("새닉네임");

        ResponseEntity<ApiResponse<Void>> response =
                memberController.editNickname(request, claims);

        verify(memberService).editNickname(2L, "새닉네임");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data()).isNull();
    }

    @Test
    void getProfileImageReturnsStoredImageMetadataAndResource() {
        Resource resource = new ByteArrayResource("image".getBytes());
        ProfileImageResult result = ProfileImageResult.stored(
                resource,
                "image/png",
                5L
        );
        when(memberService.getProfileImage(2L)).thenReturn(result);

        ResponseEntity<Resource> response =
                memberController.getProfileImage(2L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.IMAGE_PNG);
        assertThat(response.getHeaders().getContentLength()).isEqualTo(5L);
        assertThat(response.getHeaders().getCacheControl())
                .contains("max-age=3600")
                .contains("private");
        assertThat(response.getBody()).isSameAs(resource);
        verify(memberService).getProfileImage(2L);
    }

    @Test
    void profileUploadUsesAuthenticatedMemberId() {
        AccessTokenClaims claims = claims();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "profile.png",
                "image/png",
                "image".getBytes()
        );

        ResponseEntity<ApiResponse<Void>> response =
                memberController.profileUpload(file, claims);

        verify(memberService).profileUpload(2L, file);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data()).isNull();
    }

    @Test
    void applySocialProfileImageUsesAuthenticatedMemberId() {
        AccessTokenClaims claims = claims();
        SocialProfileProviderRequest request =
                new SocialProfileProviderRequest(SocialProvider.GOOGLE);

        ResponseEntity<ApiResponse<Void>> response =
                memberController.applySocialProfileImage(request, claims);

        verify(memberService).applySocialProfileImage(2L, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data()).isNull();
    }

    @Test
    void deleteProfileImageUsesAuthenticatedMemberId() {
        AccessTokenClaims claims = claims();

        ResponseEntity<ApiResponse<Void>> response =
                memberController.deleteProfileImage(claims);

        verify(memberService).deleteProfileImage(2L);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data()).isNull();
    }

    @Test
    void changePasswordExpiresCookiesAndSendsForceLogout() {
        AccessTokenClaims claims = claims();
        ChangePasswordRequest request =
                new ChangePasswordRequest("newPassword123!");
        when(authCookieFactory.deleteSessionIdCookie())
                .thenReturn("deleted-session-id-cookie");
        when(authCookieFactory.deleteRefreshTokenCookie())
                .thenReturn("deleted-refresh-token-cookie");

        ResponseEntity<ApiResponse<Void>> response =
                memberController.changePassword(
                        "reauthentication-token",
                        request,
                        claims
                );

        verify(memberService).changePassword(
                2L,
                "session-id",
                "reauthentication-token",
                ReauthenticationPurpose.CHANGE_PASSWORD,
                "newPassword123!"
        );
        verify(messagingTemplate).convertAndSendToUser(
                "2",
                "/queue/auth",
                AuthWebSocketEvent.forceLogout()
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE))
                .containsExactly(
                        "deleted-session-id-cookie",
                        "deleted-refresh-token-cookie"
                );
    }

    @Test
    void changePasswordDoesNotLogoutWhenServiceFails() {
        AccessTokenClaims claims = claims();
        ChangePasswordRequest request =
                new ChangePasswordRequest("newPassword123!");
        CustomException failure = new CustomException(
                ErrorCode.INVALID_REAUTHENTICATION_TOKEN
        );
        doThrow(failure)
                .when(memberService)
                .changePassword(
                        2L,
                        "session-id",
                        "invalid-token",
                        ReauthenticationPurpose.CHANGE_PASSWORD,
                        "newPassword123!"
                );

        assertThatThrownBy(() ->
                memberController.changePassword(
                        "invalid-token",
                        request,
                        claims
                )
        ).isSameAs(failure);

        verifyNoInteractions(
                messagingTemplate,
                authCookieFactory
        );
    }

    private AccessTokenClaims claims() {
        return new AccessTokenClaims(
                2L,
                "token-id",
                "session-id",
                true,
                Instant.now(),
                Instant.now().plusSeconds(600)
        );
    }
}
