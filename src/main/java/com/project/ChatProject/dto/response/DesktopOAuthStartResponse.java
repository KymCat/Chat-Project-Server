package com.project.ChatProject.dto.response;

public record DesktopOAuthStartResponse(
        String authorizationUrl
) {

    public static DesktopOAuthStartResponse create(String attemptId) {
        return new DesktopOAuthStartResponse(
                "/oauth2/authorization/google?desktopAttemptId="
                + attemptId
        );
    }

}
