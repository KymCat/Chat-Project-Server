package com.project.ChatProject.service;

import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.SocialAccount;
import com.project.ChatProject.entity.enums.MemberStatus;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.oauth.SocialUserProfile;
import com.project.ChatProject.repository.MemberRepository;
import com.project.ChatProject.repository.SocialAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class SocialLoginService {

    private static final String DEFAULT_NICKNAME = "사용자";
    private static final int MAX_NICKNAME_LENGTH = 30;

    private final MemberRepository memberRepository;
    private final SocialAccountRepository socialAccountRepository;

    @Transactional
    public Member login(SocialUserProfile profile) {

        validateProfile(profile);
        String email = normalizeEmail(profile.email());

        return socialAccountRepository
                .findByProviderAndProviderUserId(
                        profile.provider(),
                        profile.providerUserId()
                )
                .map(socialAccount ->
                        loginExistingSocialAccount(
                                socialAccount,
                                profile,
                                email
                        )
                )
                .orElseGet(() ->
                        createOrLinkSocialAccount(
                                profile,
                                email
                        )
                );
    }

    private void validateProfile(SocialUserProfile profile) {
        if (profile == null
                || profile.provider() == null
                || !StringUtils.hasText(profile.providerUserId())
                || !StringUtils.hasText(profile.email())
                || !profile.emailVerified()
        ) {
            throw new CustomException(
                    ErrorCode.INVALID_SOCIAL_PROFILE
            );
        }
    }

    /**
     * 이메일 정규화 : 양쪽 공백제거, 영문 소문자로 변환
     * @param email
     * @return
     */
    private String normalizeEmail(String email) {
        return email.trim()
                .toLowerCase(Locale.ROOT);
    }

    /**
     * 소셜 로그인 계정이 있을 경우, 최신 정보로 동기화 및 로그인 시간 업데이트
     * @param account
     * @param profile
     * @param email
     * @return
     */
    private Member loginExistingSocialAccount(
            SocialAccount account,      // 기존
            SocialUserProfile profile,  // 최신
            String email
    ) {
        Member member = account.getMember();
        validateMember(member);

        account.synchronizeProfile(
                email,
                profile.displayName(),      // 최신 정보
                profile.profileImageUrl()   // 최신 정보
        );

        member.updateLastLoginAt();
        return member;
    }

    /**
     * 기존 소셜 계정 정보가 없는 경우
     * @param profile
     * @param email
     * @return
     */
    private Member createOrLinkSocialAccount(
            SocialUserProfile profile,
            String email
    ) {
        Member member =
                memberRepository.findByEmail(email);

        // 기존 멤버가 아니면 신규 가입
        if (member == null) {
            member = createMember(profile, email);
        }
        // 기존 멤버지만 소셜로그인 정보가 없는 경우
        else {
            validateMember(member);
            boolean isLinked = socialAccountRepository
                    .existsByMemberIdAndProvider(
                            member.getId(),
                            profile.provider()
                    );
            if (isLinked) {
                throw new CustomException(
                        ErrorCode.SOCIAL_ACCOUNT_CONFLICT
                );
            }

            if (member.getEmailVerifiedAt() == null)
                member.verifyEmail();
        }

        SocialAccount account = SocialAccount.create(
                member,
                profile.provider(),
                profile.providerUserId(),
                email,
                profile.displayName(),
                profile.profileImageUrl()
        );
        socialAccountRepository.save(account);
        member.updateLastLoginAt();

        return member;
    }

    /**
     * 기존 소셜 계정 정보가 없고, 회원 정보 등록정보가 없는 경우 등록
     * @param profile
     * @param email
     * @return
     */
    private Member createMember(
            SocialUserProfile profile,
            String email
    ) {
        Member member = Member.create(
                profile.profileImageUrl(),
                email,
                createNickName(profile.displayName())
        );
        member.verifyEmail();
        memberRepository.save(member);

        return member;
    }

    private void validateMember(Member member) {
        if (member.getStatus().equals(MemberStatus.WITHDRAWN)) {
            throw new CustomException(
                    ErrorCode.MEMBER_WITHDRAWN
            );
        }

        if (member.getStatus().equals(MemberStatus.SUSPENDED)) {
            throw new CustomException(
                    ErrorCode.MEMBER_BLOCKED
            );
        }
    }

    /**
     * 닉네임 검증하여 반환
     * @param displayName
     * @return 검증 통과 시, 기존 닉네임 반환.
     *          통과 실패 시, 디폴트 닉네임 반환
     */
    private String createNickName(String displayName) {
        String suffix = "%06d".formatted(
                ThreadLocalRandom.current().nextInt(1_000_000)
        );
        String defaultNickname = DEFAULT_NICKNAME + suffix;

        if (!StringUtils.hasText(displayName)) {
            return defaultNickname;
        }

        String nickname = displayName
                .replaceAll("[^a-zA-Z0-9가-힣]", "");

        if (nickname.length() < 2) {
            return defaultNickname;
        }

        if (nickname.length() > MAX_NICKNAME_LENGTH) {
            return nickname.substring(0, MAX_NICKNAME_LENGTH);
        }

        return nickname;
    }
}
