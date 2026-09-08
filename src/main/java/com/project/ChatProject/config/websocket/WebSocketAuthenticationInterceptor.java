package com.project.ChatProject.config.websocket;

import com.project.ChatProject.entity.Member;
import com.project.ChatProject.jwt.AccessTokenAuthenticator;
import com.project.ChatProject.jwt.AccessTokenClaims;
import com.project.ChatProject.repository.MemberRepository;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class WebSocketAuthenticationInterceptor implements ChannelInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AccessTokenAuthenticator authenticator;
    private final MemberRepository memberRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(
                        message,
                        StompHeaderAccessor.class
                );

        if (accessor == null ||
        !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        try {
            String accessToken = resolveAccessToken(accessor);

            Authentication accessTokenAuthentication =
                    authenticator.authenticate(accessToken);

            AccessTokenClaims claims =
                    (AccessTokenClaims) accessTokenAuthentication.getPrincipal();

            Member member = memberRepository.findById(claims.memberId())
                            .orElseThrow(() ->
                                    new BadCredentialsException(
                                            "Authenticated member not found"
                                    )
                            );

            WebSocketMemberPrincipal principal =
                    new WebSocketMemberPrincipal(
                            member.getId(),
                            member.getNickname()
                    );

            Authentication webSocketAuthentication =
                    new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            accessTokenAuthentication.getAuthorities()
                    );

            accessor.setUser(webSocketAuthentication);
            return message;
        } catch (
                JwtException
                | IllegalArgumentException
                | BadCredentialsException exception
        )
        {
            throw new BadCredentialsException(
                    "Invalid access token",
                    exception
            );
        }
    }

    private String resolveAccessToken(StompHeaderAccessor accessor) {
        String authorization =
                accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);

        if (!StringUtils.hasText(authorization)) {
            throw new BadCredentialsException(
                    "Authorization header is required"
            );
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
