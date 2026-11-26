package com.project.ChatProject.config.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

@Component
public class DesktopOAuthAuthorizationRequestRepository
        // 인증 요청의 저장, 조회, 삭제 메서드를 정의한 인터페이스
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    public static final String DESKTOP_ATTEMPT_ATTRIBUTE =  "desktopOAuthAttemptId";

    // 실제로 HttpSession에 저장, 조회, 삭제하는 Spring 기본 구현체
    private final HttpSessionOAuth2AuthorizationRequestRepository delegate =
            new HttpSessionOAuth2AuthorizationRequestRepository();

    // HttpSession 에 저장된 요청 조회
    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(
            HttpServletRequest request
    ) {
        return delegate.loadAuthorizationRequest(request);
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String attemptId = request.getParameter("desktopAttemptId");

        if (authorizationRequest == null || attemptId == null) {
            delegate.saveAuthorizationRequest(authorizationRequest, request, response);
            return;
        }

        OAuth2AuthorizationRequest desktopRequest =
                OAuth2AuthorizationRequest.from(authorizationRequest)
                        .authorizationRequestUri(
                                authorizationRequest.getAuthorizationRequestUri()
                        )
                        .attributes(attributes ->
                                attributes.put(DESKTOP_ATTEMPT_ATTRIBUTE, attemptId)
                        )
                        .build();

        delegate.saveAuthorizationRequest(desktopRequest, request, response);
    }

    // Google 인증이 끝나 Spring 콜백으로 돌아왔을 때 OAuth 로그인 Filter가 호출
    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request,
            HttpServletResponse response
    ) {

        // state 비교 및 세션에서 삭제한 인증 요청 객체를 반환
        OAuth2AuthorizationRequest savedRequest =
                delegate.removeAuthorizationRequest(request, response);

        if (savedRequest != null) {
            String attemptId = savedRequest.getAttribute(DESKTOP_ATTEMPT_ATTRIBUTE);
            if (attemptId != null) {
                request.setAttribute(DESKTOP_ATTEMPT_ATTRIBUTE, attemptId);
            }
        }

        return savedRequest;
    }
}