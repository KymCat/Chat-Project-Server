package com.project.ChatProject.entity;

import com.project.ChatProject.entity.enums.SocialProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SocialAccountTest {

    @Test
    void createInitializesSocialAccount() {
        Member member = Member.create(
                null,
                "member@example.com",
                "member"
        );
        Instant beforeCreation = Instant.now();

        SocialAccount socialAccount = SocialAccount.create(
                member,
                SocialProvider.GOOGLE,
                "google-user-id",
                "user@example.com",
                "사용자",
                "https://example.com/profile.png"
        );

        Instant afterCreation = Instant.now();

        assertThat(socialAccount.getMember()).isSameAs(member);
        assertThat(socialAccount.getProvider())
                .isEqualTo(SocialProvider.GOOGLE);
        assertThat(socialAccount.getProviderUserId())
                .isEqualTo("google-user-id");
        assertThat(socialAccount.getProviderEmail())
                .isEqualTo("user@example.com");
        assertThat(socialAccount.getProviderDisplayName())
                .isEqualTo("사용자");
        assertThat(socialAccount.getProviderProfileImageUrl())
                .isEqualTo("https://example.com/profile.png");
        assertThat(socialAccount.getLastSyncedAt())
                .isBetween(beforeCreation, afterCreation);
    }

    @Test
    void synchronizeProfileUpdatesProfileAndKeepsIdentity() {
        Member member = Member.create(
                null,
                "member@example.com",
                "member"
        );
        SocialAccount socialAccount = SocialAccount.create(
                member,
                SocialProvider.GOOGLE,
                "google-user-id",
                "old@example.com",
                "이전이름",
                "https://example.com/old.png"
        );
        Instant beforeSynchronization = Instant.now();

        socialAccount.synchronizeProfile(
                "new@example.com",
                "새이름",
                "https://example.com/new.png"
        );

        Instant afterSynchronization = Instant.now();

        assertThat(socialAccount.getMember()).isSameAs(member);
        assertThat(socialAccount.getProvider())
                .isEqualTo(SocialProvider.GOOGLE);
        assertThat(socialAccount.getProviderUserId())
                .isEqualTo("google-user-id");
        assertThat(socialAccount.getProviderEmail())
                .isEqualTo("new@example.com");
        assertThat(socialAccount.getProviderDisplayName())
                .isEqualTo("새이름");
        assertThat(socialAccount.getProviderProfileImageUrl())
                .isEqualTo("https://example.com/new.png");
        assertThat(socialAccount.getLastSyncedAt())
                .isBetween(beforeSynchronization, afterSynchronization);
    }
}
