package com.project.ChatProject.controller;

import com.project.ChatProject.dto.result.AttachmentDownloadResult;
import com.project.ChatProject.dto.response.ApiResponse;
import com.project.ChatProject.dto.response.AttachmentUploadResponse;
import com.project.ChatProject.jwt.AccessTokenClaims;
import com.project.ChatProject.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/chat-rooms")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    @PostMapping(
            value = "/{roomId}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ApiResponse<AttachmentUploadResponse>> upload(
            @PathVariable Long roomId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal AccessTokenClaims claims
    )
    {

        AttachmentUploadResponse response =
                attachmentService.upload(
                        roomId,
                        file,
                        claims.memberId()
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/{roomId}/attachments/{attachmentId}")
    public ResponseEntity<Resource> download(
            @PathVariable Long roomId,
            @PathVariable Long attachmentId,
            @AuthenticationPrincipal AccessTokenClaims claims
    ) {
        AttachmentDownloadResult result =
                attachmentService.download(
                        roomId,
                        attachmentId,
                        claims.memberId()
                );

        return ResponseEntity
                .ok()
                .contentType(
                        MediaType.parseMediaType(
                                result.contentType()
                        )
                )
                .contentLength(result.sizeBytes())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        result.contentDisposition()
                )
                .body(result.resource());
    }
}
