package com.project.ChatProject.service;

import com.project.ChatProject.entity.Member;
import com.project.ChatProject.entity.enums.MemberStatus;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.repository.AdministratorRepository;
import com.project.ChatProject.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdministratorService {

    private final AdministratorRepository administratorRepository;
    private final MemberRepository memberRepository;

    public boolean hasAccess(Long memberId) {
        Member member = memberRepository
                .findById(memberId)
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.MEMBER_NOT_FOUND
                        )
                );

        boolean result = administratorRepository
                .existsById(memberId);

        return result
                && member.getStatus() == MemberStatus.ACTIVE
                && member.getEmailVerifiedAt() != null;
    }

    public void requireAccess(Long memberId) {
        if (!hasAccess(memberId)) {
            throw new CustomException(
                    ErrorCode.ADMIN_ACCESS_DENIED
            );
        }
    }
}
