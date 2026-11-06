package com.project.ChatProject.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemberTest {

    @Test
    void updateProfileImageStoresStorageMetadata() {
        Member member = Member.create(
                "member@example.com",
                "회원"
        );

        member.updateProfileImage(
                "member-profiles/2026/11/06/profile-key",
                "image/png",
                5L
        );

        assertThat(member.hasStoredProfileImage()).isTrue();
        assertThat(member.getProfileImageStorageKey())
                .isEqualTo(
                        "member-profiles/2026/11/06/profile-key"
                );
        assertThat(member.getProfileImageContentType())
                .isEqualTo("image/png");
        assertThat(member.getProfileImageSizeBytes())
                .isEqualTo(5L);
    }

    @Test
    void deleteProfileImageClearsStorageMetadata() {
        Member member = Member.create(
                "member@example.com",
                "회원"
        );

        member.updateProfileImage(
                "member-profiles/2026/11/06/profile-key",
                "image/png",
                5L
        );

        member.deleteProfileImage();

        assertThat(member.hasStoredProfileImage()).isFalse();
        assertThat(member.getProfileImageStorageKey()).isNull();
        assertThat(member.getProfileImageContentType()).isNull();
        assertThat(member.getProfileImageSizeBytes()).isNull();
    }
}
