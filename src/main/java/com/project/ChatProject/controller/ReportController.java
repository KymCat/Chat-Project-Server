package com.project.ChatProject.controller;

import com.project.ChatProject.dto.response.ApiResponse;
import com.project.ChatProject.dto.response.MessageReportDetailResponse;
import com.project.ChatProject.dto.response.MessageReportListResponse;
import com.project.ChatProject.dto.websocket.AuthWebSocketEvent;
import com.project.ChatProject.jwt.AccessTokenClaims;
import com.project.ChatProject.service.ReportService;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/reports")
public class ReportController {

    private final ReportService reportService;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedModel<MessageReportListResponse>>>
            getMessageReport(
                    @RequestParam(defaultValue = "0") @Min(0) int page,
                    @AuthenticationPrincipal AccessTokenClaims claims
    ) {
        Long memberId = claims.memberId();
        PagedModel<MessageReportListResponse> responses =
                reportService.getMessageReports(memberId, page);

        return ResponseEntity
                .ok(ApiResponse.success(responses));
    }

    @GetMapping("/{reportId}")
    public ResponseEntity<ApiResponse<MessageReportDetailResponse>> getMessageReport(
            @PathVariable Long reportId,
            @AuthenticationPrincipal AccessTokenClaims claims
    ) {
        Long memberId = claims.memberId();
        MessageReportDetailResponse response =
                reportService.getMessageReport(memberId, reportId);

        return ResponseEntity
                .ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{reportId}")
    public ResponseEntity<ApiResponse<Void>> deleteReport(
            @PathVariable Long reportId,
            @AuthenticationPrincipal AccessTokenClaims claims
    ) {
        Long memberId = claims.memberId();
        reportService.deleteReport(memberId, reportId);

        return ResponseEntity
                .ok(ApiResponse.success(null));
    }

    @PatchMapping("/{reportId}/suspension")
    public ResponseEntity<ApiResponse<Void>> memberSuspend(
            @PathVariable Long reportId,
            @AuthenticationPrincipal AccessTokenClaims claims
    ) {
        Long memberId = claims.memberId();
        Long reportedMemberId =
                reportService.memberSuspend(memberId, reportId);

        messagingTemplate.convertAndSendToUser(
                reportedMemberId.toString(),
                "/queue/auth",
                AuthWebSocketEvent.forceLogout()
        );

        return ResponseEntity
                .ok(ApiResponse.success(null));
    }
}
