package com.project.ChatProject.jwt;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AccessTokenAuthenticator authenticator;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException
    {
        try {
            String accessToken = resolveAccessToken(request);

            if (accessToken != null) {
                Authentication authentication =
                        authenticator.authenticate(accessToken);

                SecurityContextHolder.getContext()
                        .setAuthentication(authentication);
            }
        } catch (JwtException
                 | IllegalArgumentException
                 | BadCredentialsException e
        )
        {
            SecurityContextHolder.clearContext();

            authenticationEntryPoint.commence(
                    request,
                    response,
                    new BadCredentialsException("Invalid access token", e)
            );

            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!request.getMethod().equals("POST")) {
            return false;
        }

        String uri = request.getRequestURI();

        return uri.equals("/member/signup")
                || uri.equals("/auth/login")
                || uri.equals("/auth/reissue");
    }

    private String resolveAccessToken(HttpServletRequest request) {
        String authorization =
                request.getHeader(AUTHORIZATION_HEADER);

        if (!StringUtils.hasText(authorization)) {
            return null;
        }

        if (!authorization.startsWith(BEARER_PREFIX)) {
            throw new BadCredentialsException(
                    "Authorization header must use Bearer scheme"
            );
        }

        String accessToken =
                authorization.substring(BEARER_PREFIX.length());

        if (!StringUtils.hasText(accessToken)) {
            throw new BadCredentialsException(
                    "Access Token is empty"
            );
        }

        return accessToken;
    }
}
