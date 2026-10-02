package com.project.ChatProject.service;

import com.project.ChatProject.dto.response.TokenResponse;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.jwt.JwtProvider;
import com.project.ChatProject.jwt.refresh.RefreshTokenGenerator;
import com.project.ChatProject.jwt.refresh.RefreshTokenHasher;
import com.project.ChatProject.jwt.refresh.RefreshTokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthTokenService {

    private final JwtProvider jwtProvider;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final RefreshTokenHasher refreshTokenHasher;
    private final RefreshTokenStore refreshTokenStore;

    /**
     * 로그인 시, sessionId, Access/Refresh Token 발행
     * @param member
     * @return AccessToken, RefreshToken
     */
    public TokenResponse create(Member member) {
        String sessionId = UUID.randomUUID().toString();

        return issue(member, sessionId);
    }

    /**
     * 재발행을 위한 create 메서드
     * @param member
     * @param sessionId
     * @return 새로운 AccessToken, RefreshToken
     */
    public TokenResponse create(Member member, String sessionId) {
        return issue(member, sessionId);
    }

    /**
     * 토큰 재발행을 위한 토큰 검증
     * @param refreshToken
     * @param expectedRefreshTokenHash
     */
    public void validateRefreshToken(
            String refreshToken,
            String expectedRefreshTokenHash
    ) {

        // 쿠키에서 가져온 토큰과 기존 토큰과 비교
        String refreshTokenHash = refreshTokenHasher.hash(refreshToken);
        if (!refreshTokenHash.equals(expectedRefreshTokenHash)) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    /**
     * 토큰 발행 로직
     * @param member
     * @param sessionId
     * @return AccessToken, RefreshToken
     */
    private TokenResponse issue(
            Member member,
            String sessionId
    ) {
        boolean emailVerified = member.getEmailVerifiedAt() != null;

        String accessToken = jwtProvider.generateAccessToken(
                member.getId(),
                sessionId,
                emailVerified
        );

        String refreshToken = refreshTokenGenerator.generate();
        String refreshTokenHash = refreshTokenHasher.hash(refreshToken);
        refreshTokenStore.save(
                sessionId,
                member.getId(),
                refreshTokenHash
        );

        return new TokenResponse(
                accessToken,
                refreshToken,
                sessionId
        );
    }
}

