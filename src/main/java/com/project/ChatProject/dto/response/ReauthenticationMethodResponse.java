package com.project.ChatProject.dto.response;

import com.project.ChatProject.dto.enums.ReauthenticationType;
import com.project.ChatProject.entity.enums.SocialProvider;

public record ReauthenticationMethodResponse(
        ReauthenticationType type,
        SocialProvider provider
) {

    public static ReauthenticationMethodResponse password() {
        return new ReauthenticationMethodResponse(
                ReauthenticationType.PASSWORD,
                null
        );
    }

    public static ReauthenticationMethodResponse social(
            SocialProvider provider
    ) {
        return new ReauthenticationMethodResponse(
                ReauthenticationType.SOCIAL,
                provider
        );
    }
}
