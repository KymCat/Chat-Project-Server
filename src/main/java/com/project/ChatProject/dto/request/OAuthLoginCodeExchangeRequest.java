package com.project.ChatProject.dto.request;

import jakarta.validation.constraints.NotBlank;

public record OAuthLoginCodeExchangeRequest(

        @NotBlank
        String code
) {
}
