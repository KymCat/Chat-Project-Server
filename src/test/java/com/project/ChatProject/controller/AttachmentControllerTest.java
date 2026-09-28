package com.project.ChatProject.controller;

import com.project.ChatProject.dto.response.AttachmentUploadResponse;
import com.project.ChatProject.entity.enums.ChatMessageType;
import com.project.ChatProject.jwt.AccessTokenClaims;
import com.project.ChatProject.service.AttachmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AttachmentControllerTest {

    @Mock
    private AttachmentService attachmentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AttachmentController controller =
                new AttachmentController(attachmentService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setCustomArgumentResolvers(
                        authenticationPrincipalResolver(claims())
                )
                .build();
    }

    @Test
    void uploadReturnsCreatedAttachment() throws Exception {
        org.springframework.mock.web.MockMultipartFile file =
                new org.springframework.mock.web.MockMultipartFile(
                        "file",
                        "photo.png",
                        MediaType.IMAGE_PNG_VALUE,
                        "image-content".getBytes()
                );
        AttachmentUploadResponse response =
                new AttachmentUploadResponse(
                        100L,
                        ChatMessageType.IMAGE,
                        "photo.png",
                        MediaType.IMAGE_PNG_VALUE,
                        file.getSize()
                );
        when(attachmentService.upload(10L, file, 1L))
                .thenReturn(response);

        mockMvc.perform(multipart("/chat-rooms/10/attachments")
                        .file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.attachmentId").value(100L))
                .andExpect(jsonPath("$.data.messageType").value("IMAGE"))
                .andExpect(jsonPath("$.data.originalName").value("photo.png"))
                .andExpect(jsonPath("$.data.contentType").value("image/png"))
                .andExpect(jsonPath("$.data.sizeBytes").value(file.getSize()))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist());

        verify(attachmentService).upload(10L, file, 1L);
    }

    @Test
    void uploadRejectsRequestWithoutFilePart() throws Exception {
        mockMvc.perform(multipart("/chat-rooms/10/attachments"))
                .andExpect(status().isBadRequest());

        verify(attachmentService, never()).upload(
                any(Long.class),
                any(),
                any(Long.class)
        );
    }

    @Test
    void uploadRejectsNonMultipartRequest() throws Exception {
        mockMvc.perform(post("/chat-rooms/10/attachments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnsupportedMediaType());

        verify(attachmentService, never()).upload(
                any(Long.class),
                any(),
                any(Long.class)
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
                true,
                Instant.now(),
                Instant.now().plusSeconds(600)
        );
    }
}
