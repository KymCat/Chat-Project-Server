package com.project.ChatProject.service;

import com.project.ChatProject.email.*;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.enums.MemberStatus;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final MemberRepository memberRepository;
    private final EmailVerificationCodeGenerator codeGenerator;
    private final EmailVerificationCodeHasher codeHasher;
    private final EmailVerificationStore verificationStore;
    private final EmailVerificationProperties properties;
    private final EmailSender emailSender;

    public void request(Long memberId) {
        Member member = findActiveMember(memberId);
        validateEmailNotVerified(member);

        boolean cooldownStarted =
                verificationStore.tryStartResendCooldown(memberId);

        if (!cooldownStarted) {
            throw new CustomException(
                    ErrorCode.EMAIL_VERIFICATION_REQUEST_TOO_FREQUENT
            );
        }

        String code = codeGenerator.generate();
        String codeHash = codeHasher.hash(code);

        verificationStore.save(
                memberId,
                codeHash
        );

        try {
            emailSender.sendVerificationCode(
                    member.getEmail(),
                    code
            );

            log.info(
                    "event=email_verification_send_accepted memberId = {}",
                    memberId
            );
        } catch (MailException exception) {
            log.error(
                    "event=email_verification_send_failed memberId={} exceptionType={}",
                    memberId,
                    exception.getClass().getSimpleName()
            );

            verificationStore.deleteByMemberId(memberId);
            verificationStore.deleteResendCooldown(memberId);

            throw new CustomException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    @Transactional
    public void confirm(
            Long memberId,
            String code
    )
    {
        Member member = findActiveMember(memberId);
        validateEmailNotVerified(member);

        String savedCodeHash = verificationStore
                .findCodeHashByMemberId(memberId)
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.EMAIL_VERIFICATION_NOT_FOUND
                        ));

        if (!codeHasher.matches(code, savedCodeHash)) {
            handleVerificationFailure(memberId);
        }

        member.verifyEmail();
        verificationStore.deleteByMemberId(memberId);

        log.info(
                "event=email_verification_confirmed memberId={}",
                memberId
        );
    }

    private void handleVerificationFailure(Long memberId) {
        long attemptCount = verificationStore
                .incrementAttemptCount(memberId)
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.EMAIL_VERIFICATION_NOT_FOUND
                        ));
        if (attemptCount >= properties.maxAttempts()) {
            log.warn(
                    "event=email_verification_attempts_exceeded memberId={} attempts={}",
                    memberId,
                    attemptCount
            );

            verificationStore.deleteByMemberId(memberId);
        }

        throw new CustomException(
                ErrorCode.INVALID_EMAIL_VERIFICATION_CODE
        );
    }

    private Member findActiveMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() ->
                        new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        validateMemberStatus(member);
        return member;
    }

    private void validateEmailNotVerified(Member member) {
        if (member.getEmailVerifiedAt() != null) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_VERIFIED);
        }
    }

    private void validateMemberStatus(Member member) {
        if (member.getStatus() == MemberStatus.SUSPENDED) {
            throw new CustomException(
                    ErrorCode.MEMBER_BLOCKED
            );
        }

        if (member.getStatus() == MemberStatus.WITHDRAWN) {
            throw new CustomException(
                    ErrorCode.MEMBER_WITHDRAWN
            );
        }
    }
}
