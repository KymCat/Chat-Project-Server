package com.project.ChatProject.controller;

import com.project.ChatProject.dto.response.ApiResponse;
import com.project.ChatProject.jwt.AccessTokenClaims;
import com.project.ChatProject.service.AdministratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin")
public class AdministratorController {

    private final AdministratorService administratorService;

    @GetMapping("/access")
    public ResponseEntity<ApiResponse<Void>> requireAccess(
            @AuthenticationPrincipal AccessTokenClaims claims
    ) {
        Long memberId = claims.memberId();
        administratorService.requireAccess(memberId);

        return ResponseEntity
                .ok(ApiResponse.success(null));
    }
}
