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
import com.project.ChatProject.oauth.SocialUserProfile;
import com.project.ChatProject.reauth.ReauthenticationTokenStore;
import com.project.ChatProject.reauth.SocialReauthenticationAttempt;
import com.project.ChatProject.reauth.SocialReauthenticationAttemptStore;
import com.project.ChatProject.reauth.SocialReauthenticationCodeStore;
import com.project.ChatProject.repository.MemberCredentialRepository;
import com.project.ChatProject.repository.MemberRepository;
import com.project.ChatProject.repository.SocialAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

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
    private final SocialReauthenticationAttemptStore socialReauthenticationAttemptStore;
    private final SocialReauthenticationCodeStore socialReauthenticationCodeStore;

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

    @Transactional
    public String startSocialReauthentication(
            Long memberId,
            String sessionId,
            SocialProvider provider,
            ReauthenticationPurpose purpose
    ) {
        Member member = memberRepository
                .findById(memberId)
                .orElseThrow(() ->
                        new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        isMemberSuspendOrWithdrawn(member);

        if (memberCredentialRepository.existsById(memberId)) {
            throw new CustomException(
                    ErrorCode.INVALID_REAUTHENTICATION_METHOD
            );
        }

        socialAccountRepository
                .findByProviderAndMemberId(
                        provider,
                        memberId
                )
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.SOCIAL_ACCOUNT_NOT_FOUND
                        )
                );

        return socialReauthenticationAttemptStore
                .create(
                        memberId,
                        sessionId,
                        provider,
                        purpose
                );
    }

    public String completeSocialReauthentication(
            String intentId,
            SocialUserProfile profile
    ) {
        SocialReauthenticationAttempt attempt =
                socialReauthenticationAttemptStore.consume(
                        intentId
                );

        validateSocialReauthenticationProfile(
                attempt,
                profile
        );

        if (!refreshTokenStore.isActiveSession(
                attempt.memberId(),
                attempt.sessionId()
        )) {
            throw invalidSocialReauthenticationAttempt();
        }

        SocialAccount account = socialAccountRepository
                .findByProviderAndMemberId(
                        attempt.provider(),
                        attempt.memberId()
                )
                .orElseThrow(
                        this::invalidSocialReauthenticationAttempt
                );

        if (!Objects.equals(
                account.getProviderUserId(),
                profile.providerUserId()
        )) {
            throw invalidSocialReauthenticationAttempt();
        }

        String reauthenticationToken =
                reauthenticationTokenStore.create(
                        attempt.memberId(),
                        attempt.sessionId(),
                        attempt.purpose()
                );

        return socialReauthenticationCodeStore.create(
                attempt.sessionId(),
                reauthenticationToken
        );
    }

    public String exchangeSocialReauthenticationCode(
            String code,
            String sessionId
    ) {
        return socialReauthenticationCodeStore
                .consume(code, sessionId)
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.INVALID_SOCIAL_REAUTHENTICATION_CODE
                        )
                );
    }

    private void validateSocialReauthenticationProfile(
            SocialReauthenticationAttempt attempt,
            SocialUserProfile profile
    ) {
        if (profile == null
                || profile.provider() != attempt.provider()
                || !StringUtils.hasText(profile.providerUserId())) {
            throw invalidSocialReauthenticationAttempt();
        }
    }

    private CustomException invalidSocialReauthenticationAttempt() {
        return new CustomException(
                ErrorCode.INVALID_SOCIAL_REAUTHENTICATION_ATTEMPT
        );
    }

    private void isMemberSuspendOrWithdrawn(Member member) {
        if (member.getStatus() != MemberStatus.ACTIVE) {
            if (member.getStatus() == MemberStatus.SUSPENDED)
                throw new CustomException(ErrorCode.MEMBER_SUSPENDED);

            if (member.getStatus() == MemberStatus.WITHDRAWN)
                throw new CustomException(ErrorCode.MEMBER_WITHDRAWN);
        }
    }
}
