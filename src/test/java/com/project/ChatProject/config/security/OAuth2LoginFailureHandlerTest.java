package com.project.ChatProject.config.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.AuthenticationException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OAuth2LoginFailureHandlerTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private AuthenticationException exception;

    @InjectMocks
    private OAuth2LoginFailureHandler failureHandler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                failureHandler,
                "frontendCallbackUri",
                "http://localhost:3000/oauth/callback"
        );
    }

    @Test
    void oauthLoginFailureRedirectsToFrontendCallbackWithError()
            throws Exception {
        failureHandler.onAuthenticationFailure(
                request,
                response,
                exception
        );

        verify(response).sendRedirect(
                "http://localhost:3000/oauth/callback?oauthError=true"
        );
    }
}
