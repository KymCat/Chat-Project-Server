package com.project.ChatProject.service;

import com.project.ChatProject.email.EmailSender;
import com.project.ChatProject.email.EmailVerificationCodeGenerator;
import com.project.ChatProject.email.EmailVerificationCodeHasher;
import com.project.ChatProject.email.EmailVerificationProperties;
import com.project.ChatProject.email.EmailVerificationStore;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.enums.MemberStatus;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private EmailVerificationCodeGenerator codeGenerator;

    @Mock
    private EmailVerificationCodeHasher codeHasher;

    @Mock
    private EmailVerificationStore verificationStore;

    @Mock
    private EmailSender emailSender;

    private EmailVerificationService emailVerificationService;

    @BeforeEach
    void setUp() {
        EmailVerificationProperties properties =
                new EmailVerificationProperties(
                        Duration.ofMinutes(5),
                        Duration.ofSeconds(60),
                        5,
                        "no-reply@chatproject.local"
                );

        emailVerificationService = new EmailVerificationService(
                memberRepository,
                codeGenerator,
                codeHasher,
                verificationStore,
                properties,
                emailSender
        );
    }

    @Test
    void requestStoresHashedCodeAndSendsEmail() {
        Member member = activeMember();
        when(member.getEmail()).thenReturn("user@example.com");
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));
        when(verificationStore.tryStartResendCooldown(1L))
                .thenReturn(true);
        when(codeGenerator.generate()).thenReturn("123456");
        when(codeHasher.hash("123456")).thenReturn("code-hash");

        emailVerificationService.request(1L);

        verify(verificationStore).save(1L, "code-hash");
        verify(emailSender).sendVerificationCode(
                "user@example.com",
                "123456"
        );
    }

    @Test
    void requestRejectsResendDuringCooldown() {
        Member member = activeMember();
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));
        when(verificationStore.tryStartResendCooldown(1L))
                .thenReturn(false);

        assertErrorCode(
                () -> emailVerificationService.request(1L),
                ErrorCode.EMAIL_VERIFICATION_REQUEST_TOO_FREQUENT
        );

        verify(codeGenerator, never()).generate();
        verify(verificationStore, never()).save(anyLong(), anyString());
        verify(emailSender, never()).sendVerificationCode(
                anyString(),
                anyString()
        );
    }

    @Test
    void requestDeletesVerificationDataWhenEmailSendFails() {
        Member member = activeMember();
        when(member.getEmail()).thenReturn("user@example.com");
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));
        when(verificationStore.tryStartResendCooldown(1L))
                .thenReturn(true);
        when(codeGenerator.generate()).thenReturn("123456");
        when(codeHasher.hash("123456")).thenReturn("code-hash");
        doThrow(new MailSendException("SMTP failure"))
                .when(emailSender)
                .sendVerificationCode("user@example.com", "123456");

        assertErrorCode(
                () -> emailVerificationService.request(1L),
                ErrorCode.EMAIL_SEND_FAILED
        );

        verify(verificationStore).deleteByMemberId(1L);
        verify(verificationStore).deleteResendCooldown(1L);
    }

    @Test
    void requestRejectsAlreadyVerifiedMember() {
        Member member = activeMember();
        when(member.getEmailVerifiedAt()).thenReturn(Instant.now());
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));

        assertErrorCode(
                () -> emailVerificationService.request(1L),
                ErrorCode.EMAIL_ALREADY_VERIFIED
        );

        verify(verificationStore, never())
                .tryStartResendCooldown(anyLong());
    }

    @Test
    void requestRejectsSuspendedMember() {
        Member member = member(MemberStatus.SUSPENDED);
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));

        assertErrorCode(
                () -> emailVerificationService.request(1L),
                ErrorCode.MEMBER_BLOCKED
        );

        verify(verificationStore, never())
                .tryStartResendCooldown(anyLong());
    }

    @Test
    void confirmVerifiesMemberAndDeletesVerificationData() {
        Member member = activeMember();
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));
        when(verificationStore.findCodeHashByMemberId(1L))
                .thenReturn(Optional.of("saved-code-hash"));
        when(codeHasher.matches("123456", "saved-code-hash"))
                .thenReturn(true);

        emailVerificationService.confirm(1L, "123456");

        verify(member).verifyEmail();
        verify(verificationStore).deleteByMemberId(1L);
        verify(verificationStore, never()).incrementAttemptCount(anyLong());
    }

    @Test
    void confirmRejectsMissingOrExpiredVerification() {
        Member member = activeMember();
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));
        when(verificationStore.findCodeHashByMemberId(1L))
                .thenReturn(Optional.empty());

        assertErrorCode(
                () -> emailVerificationService.confirm(1L, "123456"),
                ErrorCode.EMAIL_VERIFICATION_NOT_FOUND
        );

        verify(codeHasher, never()).matches(anyString(), anyString());
        verify(member, never()).verifyEmail();
    }

    @Test
    void confirmIncrementsAttemptCountForInvalidCode() {
        Member member = activeMember();
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));
        when(verificationStore.findCodeHashByMemberId(1L))
                .thenReturn(Optional.of("saved-code-hash"));
        when(codeHasher.matches("000000", "saved-code-hash"))
                .thenReturn(false);
        when(verificationStore.incrementAttemptCount(1L))
                .thenReturn(Optional.of(1L));

        assertErrorCode(
                () -> emailVerificationService.confirm(1L, "000000"),
                ErrorCode.INVALID_EMAIL_VERIFICATION_CODE
        );

        verify(verificationStore, never()).deleteByMemberId(anyLong());
        verify(member, never()).verifyEmail();
    }

    @Test
    void confirmDeletesVerificationAtMaximumAttempts() {
        Member member = activeMember();
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));
        when(verificationStore.findCodeHashByMemberId(1L))
                .thenReturn(Optional.of("saved-code-hash"));
        when(codeHasher.matches("000000", "saved-code-hash"))
                .thenReturn(false);
        when(verificationStore.incrementAttemptCount(1L))
                .thenReturn(Optional.of(5L));

        assertErrorCode(
                () -> emailVerificationService.confirm(1L, "000000"),
                ErrorCode.INVALID_EMAIL_VERIFICATION_CODE
        );

        verify(verificationStore).deleteByMemberId(1L);
        verify(member, never()).verifyEmail();
    }

    @Test
    void confirmRejectsWithdrawnMember() {
        Member member = member(MemberStatus.WITHDRAWN);
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));

        assertErrorCode(
                () -> emailVerificationService.confirm(1L, "123456"),
                ErrorCode.MEMBER_WITHDRAWN
        );

        verify(verificationStore, never())
                .findCodeHashByMemberId(anyLong());
    }

    private Member activeMember() {
        return member(MemberStatus.ACTIVE);
    }

    private Member member(MemberStatus status) {
        Member member = org.mockito.Mockito.mock(Member.class);
        when(member.getStatus()).thenReturn(status);
        return member;
    }

    private void assertErrorCode(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
            ErrorCode expectedErrorCode
    ) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(expectedErrorCode)
                );
    }
}
