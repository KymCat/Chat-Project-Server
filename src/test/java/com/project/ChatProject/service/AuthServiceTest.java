package com.project.ChatProject.service;

import com.project.ChatProject.dto.request.LoginRequest;
import com.project.ChatProject.dto.response.TokenResponse;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.enums.MemberStatus;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.jwt.AccessTokenBlacklistStore;
import com.project.ChatProject.jwt.AccessTokenClaims;
import com.project.ChatProject.jwt.JwtProvider;
import com.project.ChatProject.jwt.refresh.RefreshTokenGenerator;
import com.project.ChatProject.jwt.refresh.RefreshTokenHasher;
import com.project.ChatProject.jwt.refresh.RefreshTokenSession;
import com.project.ChatProject.jwt.refresh.RefreshTokenStore;
import com.project.ChatProject.repository.MemberCredentialRepository;
import com.project.ChatProject.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberCredentialRepository memberCredentialRepository;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private RefreshTokenGenerator refreshTokenGenerator;

    @Mock
    private RefreshTokenHasher refreshTokenHasher;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @Mock
    private AccessTokenBlacklistStore accessTokenBlacklistStore;

    @InjectMocks
    private AuthService authService;

    @Test
    void loginReturnsTokensAndStoresHashedRefreshToken() {
        LoginRequest request = new LoginRequest(
                "  USER@EXAMPLE.COM  ",
                "password123!"
        );
        Member member = member(MemberStatus.ACTIVE);

        when(memberRepository.findByEmail("user@example.com"))
                .thenReturn(member);
        when(memberCredentialRepository.getPasswordHashById(1L))
                .thenReturn("password-hash");
        when(passwordEncoder.matches("password123!", "password-hash"))
                .thenReturn(true);
        when(jwtProvider.generateAccessToken(eq(1L), anyString(), eq(false)))
                .thenReturn("access-token");
        when(refreshTokenGenerator.generate())
                .thenReturn("refresh-token");
        when(refreshTokenHasher.hash("refresh-token"))
                .thenReturn("refresh-token-hash");

        TokenResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.sessionId()).isNotBlank();

        verify(refreshTokenStore).save(
                response.sessionId(),
                1L,
                "refresh-token-hash"
        );
        verify(jwtProvider).generateAccessToken(
                1L,
                response.sessionId(),
                false
        );
        verify(member).updateLastLoginAt();
    }

    @Test
    void loginRejectsUnknownEmail() {
        LoginRequest request = new LoginRequest(
                "missing@example.com",
                "password123!"
        );
        when(memberRepository.findByEmail("missing@example.com"))
                .thenReturn(null);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_CREDENTIALS)
                );

        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(refreshTokenStore, never()).save(
                anyString(),
                eq(1L),
                anyString()
        );
    }

    @Test
    void loginRejectsWrongPassword() {
        LoginRequest request = new LoginRequest(
                "user@example.com",
                "wrong-password"
        );
        Member member = org.mockito.Mockito.mock(Member.class);
        when(member.getId()).thenReturn(1L);

        when(memberRepository.findByEmail("user@example.com"))
                .thenReturn(member);
        when(memberCredentialRepository.getPasswordHashById(1L))
                .thenReturn("password-hash");
        when(passwordEncoder.matches("wrong-password", "password-hash"))
                .thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_CREDENTIALS)
                );

        verify(refreshTokenGenerator, never()).generate();
    }

    @Test
    void loginRejectsSuspendedMember() {
        assertLoginRejectedByMemberStatus(
                MemberStatus.SUSPENDED,
                ErrorCode.MEMBER_BLOCKED
        );
    }

    @Test
    void loginRejectsWithdrawnMember() {
        assertLoginRejectedByMemberStatus(
                MemberStatus.WITHDRAWN,
                ErrorCode.MEMBER_WITHDRAWN
        );
    }

    @Test
    void logoutBlacklistsAccessTokenAndDeletesRefreshSession() {
        Instant expiresAt = Instant.now().plusSeconds(600);
        AccessTokenClaims claims = new AccessTokenClaims(
                1L,
                "token-id",
                "session-id",
                true,
                Instant.now(),
                expiresAt
        );

        authService.logout(claims);

        verify(accessTokenBlacklistStore).save("token-id", expiresAt);
        verify(refreshTokenStore).deleteBySessionId("session-id");
    }

    @Test
    void reissueRotatesAccessTokenAndRefreshToken() {
        Member member = member(MemberStatus.ACTIVE);
        RefreshTokenSession session = new RefreshTokenSession(
                1L,
                "current-refresh-token-hash"
        );

        when(refreshTokenStore.findBySessionId("session-id"))
                .thenReturn(Optional.of(session));
        when(refreshTokenHasher.hash("current-refresh-token"))
                .thenReturn("current-refresh-token-hash");
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));
        when(jwtProvider.generateAccessToken(1L, "session-id", false))
                .thenReturn("new-access-token");
        when(refreshTokenGenerator.generate())
                .thenReturn("new-refresh-token");
        when(refreshTokenHasher.hash("new-refresh-token"))
                .thenReturn("new-refresh-token-hash");

        TokenResponse response = authService.reissue(
                "session-id",
                "current-refresh-token"
        );

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
        assertThat(response.sessionId()).isEqualTo("session-id");
        verify(refreshTokenStore).save(
                "session-id",
                1L,
                "new-refresh-token-hash"
        );
        verify(member, never()).updateLastLoginAt();
    }

    @Test
    void reissueRejectsMissingRefreshSession() {
        when(refreshTokenStore.findBySessionId("missing-session-id"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.reissue(
                "missing-session-id",
                "refresh-token"
        )).isInstanceOfSatisfying(
                CustomException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN)
        );

        verify(refreshTokenHasher, never()).hash(anyString());
        verify(memberRepository, never()).findById(anyLong());
    }

    @Test
    void reissueRejectsRefreshTokenHashMismatch() {
        RefreshTokenSession session = new RefreshTokenSession(
                1L,
                "saved-refresh-token-hash"
        );
        when(refreshTokenStore.findBySessionId("session-id"))
                .thenReturn(Optional.of(session));
        when(refreshTokenHasher.hash("invalid-refresh-token"))
                .thenReturn("invalid-refresh-token-hash");

        assertThatThrownBy(() -> authService.reissue(
                "session-id",
                "invalid-refresh-token"
        )).isInstanceOfSatisfying(
                CustomException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN)
        );

        verify(memberRepository, never()).findById(anyLong());
        verify(refreshTokenGenerator, never()).generate();
    }

    @Test
    void reissueRejectsSuspendedMember() {
        assertReissueRejectedByMemberStatus(
                MemberStatus.SUSPENDED,
                ErrorCode.MEMBER_BLOCKED
        );
    }

    @Test
    void reissueRejectsWithdrawnMember() {
        assertReissueRejectedByMemberStatus(
                MemberStatus.WITHDRAWN,
                ErrorCode.MEMBER_WITHDRAWN
        );
    }

    private void assertLoginRejectedByMemberStatus(
            MemberStatus status,
            ErrorCode expectedErrorCode
    ) {
        LoginRequest request = new LoginRequest(
                "user@example.com",
                "password123!"
        );
        Member member = member(status);

        when(memberRepository.findByEmail("user@example.com"))
                .thenReturn(member);
        when(memberCredentialRepository.getPasswordHashById(1L))
                .thenReturn("password-hash");
        when(passwordEncoder.matches("password123!", "password-hash"))
                .thenReturn(true);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(expectedErrorCode)
                );

        verify(refreshTokenGenerator, never()).generate();
    }

    private void assertReissueRejectedByMemberStatus(
            MemberStatus status,
            ErrorCode expectedErrorCode
    ) {
        RefreshTokenSession session = new RefreshTokenSession(
                1L,
                "refresh-token-hash"
        );
        Member member = org.mockito.Mockito.mock(Member.class);
        when(member.getStatus()).thenReturn(status);

        when(refreshTokenStore.findBySessionId("session-id"))
                .thenReturn(Optional.of(session));
        when(refreshTokenHasher.hash("refresh-token"))
                .thenReturn("refresh-token-hash");
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));

        assertThatThrownBy(() -> authService.reissue(
                "session-id",
                "refresh-token"
        )).isInstanceOfSatisfying(
                CustomException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(expectedErrorCode)
        );

        verify(refreshTokenGenerator, never()).generate();
        verify(refreshTokenStore, never()).save(
                anyString(),
                anyLong(),
                anyString()
        );
    }

    private Member member(MemberStatus status) {
        Member member = org.mockito.Mockito.mock(Member.class);
        when(member.getId()).thenReturn(1L);
        when(member.getStatus()).thenReturn(status);
        return member;
    }
}
