package com.project.ChatProject.config.websocket;

import com.project.ChatProject.entity.Member;
import com.project.ChatProject.jwt.AccessTokenAuthenticator;
import com.project.ChatProject.jwt.AccessTokenClaims;
import com.project.ChatProject.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthenticationInterceptorTest {

    @Mock
    private AccessTokenAuthenticator authenticator;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private Member member;

    private WebSocketAuthenticationInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketAuthenticationInterceptor(
                authenticator,
                memberRepository
        );
    }

    @Test
    void validAccessTokenStoresAuthenticationInConnectMessage() {
        StompHeaderAccessor accessor =
                createAccessor(StompCommand.CONNECT);
        accessor.setNativeHeader(
                "Authorization",
                "Bearer access-token"
        );
        Message<byte[]> message = createMessage(accessor);
        Authentication accessTokenAuthentication = authentication();
        when(authenticator.authenticate("access-token"))
                .thenReturn(accessTokenAuthentication);
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));
        when(member.getId()).thenReturn(1L);
        when(member.getNickname()).thenReturn("홍길동");

        Message<?> interceptedMessage =
                interceptor.preSend(message, null);

        assertThat(interceptedMessage).isSameAs(message);
        assertThat(accessor.getUser())
                .isInstanceOf(Authentication.class);

        Authentication webSocketAuthentication =
                (Authentication) accessor.getUser();
        assertThat(webSocketAuthentication.getPrincipal())
                .isEqualTo(
                        new WebSocketMemberPrincipal(
                                1L,
                                "홍길동"
                        )
                );
        assertThat(webSocketAuthentication.getAuthorities())
                .isEqualTo(
                        accessTokenAuthentication.getAuthorities()
                );
        verify(authenticator).authenticate("access-token");
        verify(memberRepository).findById(1L);
    }

    @Test
    void nonConnectMessagePassesWithoutAuthentication() {
        StompHeaderAccessor accessor =
                createAccessor(StompCommand.SEND);
        Message<byte[]> message = createMessage(accessor);

        Message<?> interceptedMessage =
                interceptor.preSend(message, null);

        assertThat(interceptedMessage).isSameAs(message);
        assertThat(accessor.getUser()).isNull();
        verifyNoInteractions(authenticator, memberRepository);
    }

    @Test
    void connectWithoutAuthorizationHeaderIsRejected() {
        StompHeaderAccessor accessor =
                createAccessor(StompCommand.CONNECT);
        Message<byte[]> message = createMessage(accessor);

        assertThatThrownBy(() ->
                interceptor.preSend(message, null)
        )
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid access token")
                .hasCauseInstanceOf(BadCredentialsException.class);

        verify(authenticator, never()).authenticate("access-token");
        verifyNoInteractions(memberRepository);
    }

    @Test
    void connectWithNonBearerAuthorizationHeaderIsRejected() {
        StompHeaderAccessor accessor =
                createAccessor(StompCommand.CONNECT);
        accessor.setNativeHeader(
                "Authorization",
                "Basic access-token"
        );
        Message<byte[]> message = createMessage(accessor);

        assertThatThrownBy(() ->
                interceptor.preSend(message, null)
        )
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid access token")
                .hasCauseInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(authenticator, memberRepository);
    }

    @Test
    void connectWithEmptyBearerTokenIsRejected() {
        StompHeaderAccessor accessor =
                createAccessor(StompCommand.CONNECT);
        accessor.setNativeHeader(
                "Authorization",
                "Bearer "
        );
        Message<byte[]> message = createMessage(accessor);

        assertThatThrownBy(() ->
                interceptor.preSend(message, null)
        )
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid access token")
                .hasCauseInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(authenticator, memberRepository);
    }

    @Test
    void accessTokenAuthenticationFailureRejectsConnect() {
        StompHeaderAccessor accessor =
                createAccessor(StompCommand.CONNECT);
        accessor.setNativeHeader(
                "Authorization",
                "Bearer access-token"
        );
        Message<byte[]> message = createMessage(accessor);
        when(authenticator.authenticate("access-token"))
                .thenThrow(
                        new BadCredentialsException(
                                "Blacklisted access token"
                        )
                );

        assertThatThrownBy(() ->
                interceptor.preSend(message, null)
        )
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid access token")
                .hasCauseInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(memberRepository);
    }

    @Test
    void missingAuthenticatedMemberRejectsConnect() {
        StompHeaderAccessor accessor =
                createAccessor(StompCommand.CONNECT);
        accessor.setNativeHeader(
                "Authorization",
                "Bearer access-token"
        );
        Message<byte[]> message = createMessage(accessor);
        when(authenticator.authenticate("access-token"))
                .thenReturn(authentication());
        when(memberRepository.findById(1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                interceptor.preSend(message, null)
        )
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid access token")
                .hasCauseInstanceOf(BadCredentialsException.class);

        assertThat(accessor.getUser()).isNull();
        verify(memberRepository).findById(1L);
    }

    private StompHeaderAccessor createAccessor(
            StompCommand command
    ) {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(command);
        accessor.setLeaveMutable(true);
        return accessor;
    }

    private Message<byte[]> createMessage(
            StompHeaderAccessor accessor
    ) {
        return MessageBuilder.createMessage(
                new byte[0],
                accessor.getMessageHeaders()
        );
    }

    private Authentication authentication() {
        AccessTokenClaims claims = new AccessTokenClaims(
                1L,
                "token-id",
                "session-id",
                true,
                Instant.now(),
                Instant.now().plusSeconds(600)
        );

        return new UsernamePasswordAuthenticationToken(
                claims,
                null,
                List.of()
        );
    }
}
