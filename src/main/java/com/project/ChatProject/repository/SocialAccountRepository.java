package com.project.ChatProject.repository;

import com.project.ChatProject.entity.SocialAccount;
import com.project.ChatProject.entity.enums.SocialProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SocialAccountRepository
        extends JpaRepository<SocialAccount, Long> {

    Optional<SocialAccount> findProviderAndProviderUserId(
            SocialProvider provider,
            String providerUserId
    );
}
