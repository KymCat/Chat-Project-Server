package com.project.ChatProject.controller;

import com.project.ChatProject.jwt.AccessTokenClaims;
import com.project.ChatProject.service.AdministratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdministratorControllerTest {

    private static final Long MEMBER_ID = 1L;

    @Mock
    private AdministratorService administratorService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AdministratorController controller =
                new AdministratorController(administratorService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setCustomArgumentResolvers(
                        authenticationPrincipalResolver(claims())
                )
                .build();
    }

    @Test
    void requireAccessDelegatesAuthenticatedMember() throws Exception {
        mockMvc.perform(get("/admin/access"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(administratorService).requireAccess(MEMBER_ID);
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
                MEMBER_ID,
                "token-id",
                "session-id",
                true,
                Instant.now(),
                Instant.now().plusSeconds(600)
        );
    }
}
