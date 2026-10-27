package com.project.ChatProject.dto.request;

import com.project.ChatProject.entity.enums.SocialProvider;
import jakarta.validation.constraints.NotNull;

public record SocialProfileProviderRequest(
        @NotNull(message = "소셜 Provider는 필수입니다.")
        SocialProvider provider
) {
}
