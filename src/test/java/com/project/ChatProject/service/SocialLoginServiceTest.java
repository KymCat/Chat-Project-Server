package com.project.ChatProject.service;

import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.SocialAccount;
import com.project.ChatProject.entity.enums.MemberStatus;
import com.project.ChatProject.entity.enums.SocialProvider;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.oauth.SocialUserProfile;
import com.project.ChatProject.repository.MemberRepository;
import com.project.ChatProject.repository.SocialAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialLoginServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @InjectMocks
    private SocialLoginService socialLoginService;

    @Test
    void loginSynchronizesExistingSocialAccount() {
        SocialUserProfile profile = profile(
                "  USER@EXAMPLE.COM  ",
                "새이름",
                "https://example.com/new.png"
        );
        Member member = activeMember();
        SocialAccount socialAccount = org.mockito.Mockito.mock(
                SocialAccount.class
        );

        when(socialAccountRepository.findByProviderAndProviderUserId(
                SocialProvider.GOOGLE,
                "google-user-id"
        )).thenReturn(Optional.of(socialAccount));
        when(socialAccount.getMember()).thenReturn(member);

        Member result = socialLoginService.login(profile);

        assertThat(result).isSameAs(member);
        verify(socialAccount).synchronizeProfile(
                "user@example.com",
                "새이름",
                "https://example.com/new.png"
        );
        verify(member).updateLastLoginAt();
        verifyNoInteractions(memberRepository);
        verify(socialAccountRepository, never()).save(
                org.mockito.ArgumentMatchers.any(SocialAccount.class)
        );
    }

    @Test
    void loginLinksExistingMemberWithSameVerifiedEmail() {
        SocialUserProfile profile = profile(
                "  USER@EXAMPLE.COM  ",
                "사용자",
                "https://example.com/profile.png"
        );
        Member member = activeMember();
        when(member.getId()).thenReturn(1L);
        when(member.getEmailVerifiedAt()).thenReturn(null);

        when(socialAccountRepository.findByProviderAndProviderUserId(
                SocialProvider.GOOGLE,
                "google-user-id"
        )).thenReturn(Optional.empty());
        when(memberRepository.findByEmail("user@example.com"))
                .thenReturn(member);
        when(socialAccountRepository.existsByMemberIdAndProvider(
                1L,
                SocialProvider.GOOGLE
        )).thenReturn(false);

        Member result = socialLoginService.login(profile);

        assertThat(result).isSameAs(member);
        verify(member).verifyEmail();
        verify(member).updateLastLoginAt();

        ArgumentCaptor<SocialAccount> accountCaptor =
                ArgumentCaptor.forClass(SocialAccount.class);
        verify(socialAccountRepository).save(accountCaptor.capture());

        SocialAccount savedAccount = accountCaptor.getValue();
        assertThat(savedAccount.getMember()).isSameAs(member);
        assertThat(savedAccount.getProvider())
                .isEqualTo(SocialProvider.GOOGLE);
        assertThat(savedAccount.getProviderUserId())
                .isEqualTo("google-user-id");
        assertThat(savedAccount.getProviderEmail())
                .isEqualTo("user@example.com");
        assertThat(savedAccount.getProviderDisplayName())
                .isEqualTo("사용자");
        assertThat(savedAccount.getProviderProfileImageUrl())
                .isEqualTo("https://example.com/profile.png");
    }

    @Test
    void loginCreatesNewMemberAndSocialAccount() {
        SocialUserProfile profile = profile(
                "  NEW@EXAMPLE.COM  ",
                "홍 길@동",
                "https://example.com/profile.png"
        );
        when(socialAccountRepository.findByProviderAndProviderUserId(
                SocialProvider.GOOGLE,
                "google-user-id"
        )).thenReturn(Optional.empty());
        when(memberRepository.findByEmail("new@example.com"))
                .thenReturn(null);

        Member result = socialLoginService.login(profile);

        ArgumentCaptor<Member> memberCaptor =
                ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(memberCaptor.capture());

        Member savedMember = memberCaptor.getValue();
        assertThat(result).isSameAs(savedMember);
        assertThat(savedMember.getEmail()).isEqualTo("new@example.com");
        assertThat(savedMember.getNickname()).isEqualTo("홍길동");
        assertThat(savedMember.getProfileImageUrl())
                .isEqualTo("https://example.com/profile.png");
        assertThat(savedMember.getEmailVerifiedAt()).isNotNull();
        assertThat(savedMember.getLastLoginAt()).isNotNull();
        assertThat(savedMember.getStatus()).isEqualTo(MemberStatus.ACTIVE);

        ArgumentCaptor<SocialAccount> accountCaptor =
                ArgumentCaptor.forClass(SocialAccount.class);
        verify(socialAccountRepository).save(accountCaptor.capture());

        SocialAccount savedAccount = accountCaptor.getValue();
        assertThat(savedAccount.getMember()).isSameAs(savedMember);
        assertThat(savedAccount.getProvider())
                .isEqualTo(SocialProvider.GOOGLE);
        assertThat(savedAccount.getProviderUserId())
                .isEqualTo("google-user-id");
        assertThat(savedAccount.getProviderEmail())
                .isEqualTo("new@example.com");
    }

    @Test
    void loginUsesDefaultNicknameWhenDisplayNameIsBlank() {
        SocialUserProfile profile = profile(
                "user@example.com",
                "   ",
                null
        );
        prepareNewMemberLogin();

        socialLoginService.login(profile);

        ArgumentCaptor<Member> memberCaptor =
                ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getNickname())
                .matches("사용자\\d{6}");
    }

    @Test
    void loginTruncatesNicknameToThirtyCharacters() {
        SocialUserProfile profile = profile(
                "user@example.com",
                "가".repeat(31),
                null
        );
        prepareNewMemberLogin();

        socialLoginService.login(profile);

        ArgumentCaptor<Member> memberCaptor =
                ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getNickname())
                .isEqualTo("가".repeat(30));
    }

    @ParameterizedTest
    @MethodSource("invalidProfiles")
    void loginRejectsInvalidProfile(SocialUserProfile profile) {
        assertThatThrownBy(() -> socialLoginService.login(profile))
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_SOCIAL_PROFILE)
                );

        verifyNoInteractions(
                memberRepository,
                socialAccountRepository
        );
    }

    @ParameterizedTest
    @MethodSource("inactiveMemberStatuses")
    void loginRejectsInactiveMember(
            MemberStatus status,
            ErrorCode expectedErrorCode
    ) {
        SocialUserProfile profile = profile(
                "user@example.com",
                "사용자",
                null
        );
        Member member = org.mockito.Mockito.mock(Member.class);
        SocialAccount socialAccount = org.mockito.Mockito.mock(
                SocialAccount.class
        );

        when(member.getStatus()).thenReturn(status);
        when(socialAccount.getMember()).thenReturn(member);
        when(socialAccountRepository.findByProviderAndProviderUserId(
                SocialProvider.GOOGLE,
                "google-user-id"
        )).thenReturn(Optional.of(socialAccount));

        assertThatThrownBy(() -> socialLoginService.login(profile))
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(expectedErrorCode)
                );

        verify(socialAccount, never()).synchronizeProfile(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
        verify(member, never()).updateLastLoginAt();
    }

    @Test
    void loginRejectsDifferentAccountForAlreadyLinkedProvider() {
        SocialUserProfile profile = profile(
                "user@example.com",
                "사용자",
                null
        );
        Member member = activeMember();
        when(member.getId()).thenReturn(1L);

        when(socialAccountRepository.findByProviderAndProviderUserId(
                SocialProvider.GOOGLE,
                "google-user-id"
        )).thenReturn(Optional.empty());
        when(memberRepository.findByEmail("user@example.com"))
                .thenReturn(member);
        when(socialAccountRepository.existsByMemberIdAndProvider(
                1L,
                SocialProvider.GOOGLE
        )).thenReturn(true);

        assertThatThrownBy(() -> socialLoginService.login(profile))
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.SOCIAL_ACCOUNT_CONFLICT)
                );

        verify(socialAccountRepository, never()).save(
                org.mockito.ArgumentMatchers.any(SocialAccount.class)
        );
        verify(member, never()).verifyEmail();
        verify(member, never()).updateLastLoginAt();
    }

    private void prepareNewMemberLogin() {
        when(socialAccountRepository.findByProviderAndProviderUserId(
                SocialProvider.GOOGLE,
                "google-user-id"
        )).thenReturn(Optional.empty());
        when(memberRepository.findByEmail("user@example.com"))
                .thenReturn(null);
    }

    private Member activeMember() {
        Member member = org.mockito.Mockito.mock(Member.class);
        when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        return member;
    }

    private SocialUserProfile profile(
            String email,
            String displayName,
            String profileImageUrl
    ) {
        return new SocialUserProfile(
                SocialProvider.GOOGLE,
                "google-user-id",
                email,
                true,
                displayName,
                profileImageUrl
        );
    }

    private static Stream<Arguments> invalidProfiles() {
        return Stream.of(
                Arguments.of((SocialUserProfile) null),
                Arguments.of(new SocialUserProfile(
                        null,
                        "google-user-id",
                        "user@example.com",
                        true,
                        "사용자",
                        null
                )),
                Arguments.of(new SocialUserProfile(
                        SocialProvider.GOOGLE,
                        "   ",
                        "user@example.com",
                        true,
                        "사용자",
                        null
                )),
                Arguments.of(new SocialUserProfile(
                        SocialProvider.GOOGLE,
                        "google-user-id",
                        "   ",
                        true,
                        "사용자",
                        null
                )),
                Arguments.of(new SocialUserProfile(
                        SocialProvider.GOOGLE,
                        "google-user-id",
                        "user@example.com",
                        false,
                        "사용자",
                        null
                ))
        );
    }

    private static Stream<Arguments> inactiveMemberStatuses() {
        return Stream.of(
                Arguments.of(
                        MemberStatus.SUSPENDED,
                        ErrorCode.MEMBER_BLOCKED
                ),
                Arguments.of(
                        MemberStatus.WITHDRAWN,
                        ErrorCode.MEMBER_WITHDRAWN
                )
        );
    }
}
