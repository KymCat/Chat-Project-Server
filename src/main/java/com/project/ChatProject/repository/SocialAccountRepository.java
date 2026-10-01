package com.project.ChatProject.repository;

import com.project.ChatProject.entity.SocialAccount;
import com.project.ChatProject.entity.enums.SocialProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SocialAccountRepository
        extends JpaRepository<SocialAccount, Long> {

    Optional<SocialAccount> findByProviderAndProviderUserId(
            SocialProvider provider,
            String providerUserId
    );

    // 서로 다른 소셜계정 중복 연결 방지를 위한
    boolean existsByMemberIdAndProvider(
            Long memberId,
            SocialProvider provider
    );
}
