package com.project.ChatProject.controller;

import com.project.ChatProject.dto.request.SocialProfileProviderRequest;
import com.project.ChatProject.dto.result.ProfileImageResult;
import com.project.ChatProject.dto.response.ApiResponse;
import com.project.ChatProject.dto.response.MemberProfileResponse;
import com.project.ChatProject.entity.enums.SocialProvider;
import com.project.ChatProject.jwt.AccessTokenClaims;
import com.project.ChatProject.service.MemberService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.net.URI;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberControllerTest {

    @Mock
    private MemberService memberService;

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
                null,
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
        assertThat(response.getBody()).isSameAs(resource);
        verify(memberService).getProfileImage(2L);
    }

    @Test
    void getProfileImageRedirectsToExternalProfileUrl() {
        String externalUrl = "https://example.com/profile.png";
        when(memberService.getProfileImage(2L))
                .thenReturn(ProfileImageResult.external(externalUrl));

        ResponseEntity<Resource> response =
                memberController.getProfileImage(2L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation())
                .isEqualTo(URI.create(externalUrl));
        assertThat(response.getBody()).isNull();
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
