package com.project.ChatProject.controller;

import com.project.ChatProject.dto.response.MessageReportDetailResponse;
import com.project.ChatProject.dto.response.MessageReportListResponse;
import com.project.ChatProject.dto.result.AttachmentDownloadResult;
import com.project.ChatProject.dto.websocket.AuthWebSocketEvent;
import com.project.ChatProject.entity.enums.ChatMessageType;
import com.project.ChatProject.entity.enums.MemberStatus;
import com.project.ChatProject.entity.enums.MessageReportReason;
import com.project.ChatProject.jwt.AccessTokenClaims;
import com.project.ChatProject.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.data.domain.Page;
import org.springframework.data.web.PagedModel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long REPORT_ID = 100L;
    private static final Long REPORTED_MEMBER_ID = 2L;

    @Mock
    private ReportService reportService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ReportController controller = new ReportController(
                reportService,
                messagingTemplate
        );

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setCustomArgumentResolvers(
                        authenticationPrincipalResolver(claims())
                )
                .build();
    }

    @Test
    void deleteReportDelegatesAuthenticatedAdministrator() throws Exception {
        mockMvc.perform(delete("/api/admin/reports/{reportId}", REPORT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(reportService).deleteReport(ADMIN_ID, REPORT_ID);
    }

    @Test
    void getMessageReportsUsesDefaultPage() throws Exception {
        PagedModel<MessageReportListResponse> response =
                new PagedModel<>(Page.empty());
        when(reportService.getMessageReports(ADMIN_ID, 0))
                .thenReturn(response);

        mockMvc.perform(get("/api/admin/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(reportService).getMessageReports(ADMIN_ID, 0);
    }

    @Test
    void getMessageReportsPassesRequestedPage() throws Exception {
        PagedModel<MessageReportListResponse> response =
                new PagedModel<>(Page.empty());
        when(reportService.getMessageReports(ADMIN_ID, 3))
                .thenReturn(response);

        mockMvc.perform(get("/api/admin/reports")
                        .queryParam("page", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(reportService).getMessageReports(ADMIN_ID, 3);
    }

    @Test
    void getMessageReportsRejectsNegativePage() throws Exception {
        mockMvc.perform(get("/api/admin/reports")
                        .queryParam("page", "-1"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(reportService, messagingTemplate);
    }

    @Test
    void getMessageReportDelegatesAuthenticatedAdministrator()
            throws Exception {
        MessageReportDetailResponse response =
                new MessageReportDetailResponse(
                        REPORT_ID,
                        MessageReportReason.SPAM,
                        null,
                        Instant.parse("2026-11-23T00:00:00Z"),
                        3L,
                        "신고자",
                        REPORTED_MEMBER_ID,
                        "reported@example.com",
                        "신고 대상",
                        MemberStatus.ACTIVE,
                        10L,
                        20L,
                        ChatMessageType.TEXT,
                        "신고 메시지",
                        null,
                        Instant.parse("2026-11-22T23:59:00Z"),
                        null,
                        false
                );
        when(reportService.getMessageReport(ADMIN_ID, REPORT_ID))
                .thenReturn(response);

        mockMvc.perform(get("/api/admin/reports/{reportId}", REPORT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reportId").value(REPORT_ID))
                .andExpect(jsonPath("$.data.reportedMemberId")
                        .value(REPORTED_MEMBER_ID));

        verify(reportService).getMessageReport(ADMIN_ID, REPORT_ID);
    }

    @Test
    void getAttachmentReturnsResourceWithDownloadHeaders()
            throws Exception {
        byte[] imageContent = "image-content".getBytes();
        ByteArrayResource resource =
                new ByteArrayResource(imageContent);
        AttachmentDownloadResult result =
                new AttachmentDownloadResult(
                        resource,
                        "image.png",
                        MediaType.IMAGE_PNG_VALUE,
                        imageContent.length
                );
        when(reportService.getAttachment(ADMIN_ID, REPORT_ID))
                .thenReturn(result);

        mockMvc.perform(get(
                        "/api/admin/reports/{reportId}/attachment",
                        REPORT_ID
                ))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(imageContent))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ))
                .andExpect(header().longValue(
                        HttpHeaders.CONTENT_LENGTH,
                        imageContent.length
                ))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        containsString("inline")
                ));

        verify(reportService).getAttachment(ADMIN_ID, REPORT_ID);
    }

    @Test
    void memberSuspendPublishesForceLogoutToReportedMember()
            throws Exception {
        when(reportService.memberSuspend(ADMIN_ID, REPORT_ID))
                .thenReturn(REPORTED_MEMBER_ID);

        mockMvc.perform(patch(
                        "/api/admin/reports/{reportId}/suspension",
                        REPORT_ID
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(reportService).memberSuspend(ADMIN_ID, REPORT_ID);
        verify(messagingTemplate).convertAndSendToUser(
                REPORTED_MEMBER_ID.toString(),
                "/queue/auth",
                AuthWebSocketEvent.forceLogout()
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
                ADMIN_ID,
                "token-id",
                "session-id",
                true,
                Instant.now(),
                Instant.now().plusSeconds(600)
        );
    }
}
