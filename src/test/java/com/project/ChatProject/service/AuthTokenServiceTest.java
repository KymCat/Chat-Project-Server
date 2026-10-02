package com.project.ChatProject.service;

import com.project.ChatProject.dto.response.TokenResponse;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.jwt.JwtProvider;
import com.project.ChatProject.jwt.refresh.RefreshTokenGenerator;
import com.project.ChatProject.jwt.refresh.RefreshTokenHasher;
import com.project.ChatProject.jwt.refresh.RefreshTokenStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthTokenServiceTest {

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private RefreshTokenGenerator refreshTokenGenerator;

    @Mock
    private RefreshTokenHasher refreshTokenHasher;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @InjectMocks
    private AuthTokenService authTokenService;

    @Test
    void createIssuesTokensWithNewSessionId() {
        Member member = member(false);

        when(jwtProvider.generateAccessToken(eq(1L), anyString(), eq(false)))
                .thenReturn("access-token");
        when(refreshTokenGenerator.generate())
                .thenReturn("refresh-token");
        when(refreshTokenHasher.hash("refresh-token"))
                .thenReturn("refresh-token-hash");

        TokenResponse response = authTokenService.create(member);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.sessionId()).isNotBlank();
        verify(jwtProvider).generateAccessToken(
                1L,
                response.sessionId(),
                false
        );
        verify(refreshTokenStore).save(
                response.sessionId(),
                1L,
                "refresh-token-hash"
        );
    }

    @Test
    void createIssuesTokensWithExistingSessionId() {
        Member member = member(true);

        when(jwtProvider.generateAccessToken(1L, "session-id", true))
                .thenReturn("new-access-token");
        when(refreshTokenGenerator.generate())
                .thenReturn("new-refresh-token");
        when(refreshTokenHasher.hash("new-refresh-token"))
                .thenReturn("new-refresh-token-hash");

        TokenResponse response = authTokenService.create(member, "session-id");

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
        assertThat(response.sessionId()).isEqualTo("session-id");
        verify(refreshTokenStore).save(
                "session-id",
                1L,
                "new-refresh-token-hash"
        );
    }

    @Test
    void validateRefreshTokenAcceptsMatchingHash() {
        when(refreshTokenHasher.hash("refresh-token"))
                .thenReturn("refresh-token-hash");

        assertThatCode(() -> authTokenService.validateRefreshToken(
                "refresh-token",
                "refresh-token-hash"
        )).doesNotThrowAnyException();

        verify(refreshTokenHasher).hash("refresh-token");
    }

    @Test
    void validateRefreshTokenRejectsMismatchedHash() {
        when(refreshTokenHasher.hash("invalid-refresh-token"))
                .thenReturn("invalid-refresh-token-hash");

        assertThatThrownBy(() -> authTokenService.validateRefreshToken(
                "invalid-refresh-token",
                "saved-refresh-token-hash"
        )).isInstanceOfSatisfying(
                CustomException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN)
        );
    }

    private Member member(boolean emailVerified) {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(1L);
        when(member.getEmailVerifiedAt())
                .thenReturn(emailVerified ? java.time.Instant.now() : null);
        return member;
    }
}
