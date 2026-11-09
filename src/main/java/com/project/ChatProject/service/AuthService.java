package com.project.ChatProject.service;

import com.project.ChatProject.dto.enums.ReauthenticationPurpose;
import com.project.ChatProject.dto.request.LoginRequest;
import com.project.ChatProject.dto.response.ReauthenticationMethodResponse;
import com.project.ChatProject.dto.response.TokenResponse;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.SocialAccount;
import com.project.ChatProject.entity.enums.MemberStatus;
import com.project.ChatProject.entity.enums.SocialProvider;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.jwt.AccessTokenBlacklistStore;
import com.project.ChatProject.jwt.AccessTokenClaims;
import com.project.ChatProject.jwt.refresh.RefreshTokenSession;
import com.project.ChatProject.jwt.refresh.RefreshTokenStore;
import com.project.ChatProject.oauth.OAuthLoginCodeStore;
import com.project.ChatProject.reauth.ReauthenticationTokenStore;
import com.project.ChatProject.repository.MemberCredentialRepository;
import com.project.ChatProject.repository.MemberRepository;
import com.project.ChatProject.repository.SocialAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final PasswordEncoder passwordEncoder;
    private final MemberRepository memberRepository;
    private final MemberCredentialRepository memberCredentialRepository;
    private final RefreshTokenStore refreshTokenStore;
    private final AccessTokenBlacklistStore accessTokenBlacklistStore;
    private final AuthTokenService authTokenService;
    private final OAuthLoginCodeStore oauthLoginCodeStore;
    private final SocialAccountRepository socialAccountRepository;
    private final ReauthenticationTokenStore reauthenticationTokenStore;

    @Transactional
    public TokenResponse login(LoginRequest request) {

        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);
        String password = request.password();

        Member member = memberRepository.findByEmail(email);
        if (member == null) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        String passwordHash = memberCredentialRepository
                .getPasswordHashById(member.getId())
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.INVALID_CREDENTIALS
                        )
                );

        if (!passwordEncoder.matches(password, passwordHash)) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        isMemberSuspendOrWithdrawn(member);

        TokenResponse response = authTokenService.create(member);

        member.updateLastLoginAt();
        return response;
    }

    public void logout(AccessTokenClaims claims) {
        String sessionId = claims.sessionId();
        String jti = claims.tokenId();
        Instant expiresAt = claims.expiresAt();

        accessTokenBlacklistStore.save(jti, expiresAt);
        refreshTokenStore.deleteBySessionId(sessionId);
    }

    public TokenResponse reissue(
            String sessionId,
            String refreshToken
    ) {
        RefreshTokenSession session = refreshTokenStore
                .findBySessionId(sessionId)
                .orElseThrow(() ->
                        new CustomException(ErrorCode.INVALID_REFRESH_TOKEN));

        authTokenService.validateRefreshToken(
                refreshToken,
                session.refreshTokenHash()
        );

        Member member = memberRepository
                .findById(session.memberId())
                .orElseThrow(() ->
                        new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        isMemberSuspendOrWithdrawn(member);

        return authTokenService.create(
                member,
                sessionId
        );
    }

    public String exchangeOAuthLoginCode(
            String code,
            String sessionId
    ) {
        return oauthLoginCodeStore.consume(code, sessionId)
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.INVALID_OAUTH_LOGIN_CODE
                        )
                );
    }

    public void logoutAllSessions(
            Long memberId,
            String sessionId,
            String token
    ) {
        reauthenticationTokenStore.consume(
                token,
                memberId,
                sessionId,
                ReauthenticationPurpose.LOGOUT_ALL
        );

        refreshTokenStore.deleteAllByMemberId(memberId);
    }

    @Transactional(readOnly = true)
    public ReauthenticationMethodResponse getReauthenticationMethod(Long memberId) {

        Member member = memberRepository
                .findById(memberId)
                .orElseThrow(() ->
                        new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        isMemberSuspendOrWithdrawn(member);

        if (memberCredentialRepository.existsById(memberId)) {
            return ReauthenticationMethodResponse.password();
        }

        SocialAccount account = socialAccountRepository
                .findByProviderAndMemberId(
                        SocialProvider.GOOGLE,
                        memberId
                )
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.SOCIAL_ACCOUNT_NOT_FOUND
                        )
                );

        return ReauthenticationMethodResponse.social(
                account.getProvider()
        );
    }

    @Transactional
    public String reauthenticateWithPassword(
            Long memberId,
            String sessionId,
            String password,
            ReauthenticationPurpose purpose
    ) {
        String passwordHash = memberCredentialRepository
                .getPasswordHashById(memberId)
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.CREDENTIALS_NOT_FOUND
                        )
                );

        if (!passwordEncoder.matches(password, passwordHash)) {
            throw new CustomException(
                    ErrorCode.INVALID_CREDENTIALS
            );
        }

        return reauthenticationTokenStore
                .create(
                        memberId,
                        sessionId,
                        purpose
                );
    }

    private void isMemberSuspendOrWithdrawn(Member member) {
        if (member.getStatus() != MemberStatus.ACTIVE) {
            if (member.getStatus() == MemberStatus.SUSPENDED)
                throw new CustomException(ErrorCode.MEMBER_BLOCKED);

            if (member.getStatus() == MemberStatus.WITHDRAWN)
                throw new CustomException(ErrorCode.MEMBER_WITHDRAWN);
        }
    }
}
