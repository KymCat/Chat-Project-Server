package com.project.ChatProject.service;

import com.project.ChatProject.dto.request.LoginRequest;
import com.project.ChatProject.dto.response.LoginResponse;
import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.enums.MemberStatus;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.jwt.JwtProvider;
import com.project.ChatProject.jwt.refresh.RefreshTokenGenerator;
import com.project.ChatProject.jwt.refresh.RefreshTokenHasher;
import com.project.ChatProject.jwt.refresh.RefreshTokenStore;
import com.project.ChatProject.repository.MemberCredentialRepository;
import com.project.ChatProject.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final PasswordEncoder passwordEncoder;
    private final MemberRepository memberRepository;
    private final MemberCredentialRepository memberCredentialRepository;
    private final JwtProvider jwtProvider;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final RefreshTokenHasher refreshTokenHasher;
    private final RefreshTokenStore refreshTokenStore;

    @Transactional
    public LoginResponse login(LoginRequest request) {

        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);
        String password = request.password();

        Member member = memberRepository.findByEmail(email);
        if (member == null) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        String passwordHash = memberCredentialRepository
                .getPasswordHashById(member.getId());

        if (!passwordEncoder.matches(password, passwordHash)) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (member.getStatus() != MemberStatus.ACTIVE) {
            if (member.getStatus() == MemberStatus.SUSPENDED)
                throw new CustomException(ErrorCode.MEMBER_BLOCKED);

            if (member.getStatus() == MemberStatus.WITHDRAWN)
                throw new CustomException(ErrorCode.MEMBER_WITHDRAWN);
        }

        Long memberId = member.getId();
        boolean emailVerified = member.getEmailVerifiedAt() != null;
        String sessionId = UUID.randomUUID().toString();

        String accessToken = jwtProvider.generateAccessToken(
                memberId,
                sessionId,
                emailVerified
        );

        String refreshToken = refreshTokenGenerator.generate();
        String refreshTokenHash = refreshTokenHasher.hash(refreshToken);
        refreshTokenStore.save(
                sessionId,
                memberId,
                refreshTokenHash
        );

        member.updateLastLoginAt();

        return new LoginResponse(
                accessToken,
                refreshToken,
                sessionId
        );
    }
}
