package com.project.ChatProject.service;

import com.project.ChatProject.dto.request.SignupRequest;
import com.project.ChatProject.dto.response.SignupResponse;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.MemberCredential;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.repository.MemberCredentialRepository;
import com.project.ChatProject.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class MemberService {
    private final PasswordEncoder passwordEncoder;
    private final MemberRepository memberRepository;
    private final MemberCredentialRepository memberCredentialRepository;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);
        if (memberRepository.existsByEmail(email))
            throw new CustomException(ErrorCode.DUPLICATE_MEMBER_EMAIL);

        String passwordHash = passwordEncoder.encode(request.password());

        Member member = Member.create(
                request.profileImageUrl(),
                email,
                request.nickname());

        Member memberSaved = memberRepository.saveAndFlush(member);
        memberCredentialRepository.save(
                MemberCredential.create(member, passwordHash)
        );

        return SignupResponse.from(memberSaved);
    }
}
