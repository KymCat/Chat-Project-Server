package com.project.ChatProject.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemberTest {

    @Test
    void synchronizeExternalProfileImageUpdatesMemberWithoutStoredImage() {
        Member member = Member.createWithExternalProfileImage(
                "https://example.com/old.png",
                "member@example.com",
                "회원"
        );

        member.synchronizeExternalProfileImage(
                "https://example.com/new.png"
        );

        assertThat(member.getProfileImageUrl())
                .isEqualTo("https://example.com/new.png");
    }

    @Test
    void synchronizeExternalProfileImagePreservesStoredImage() {
        Member member = Member.createWithExternalProfileImage(
                "https://example.com/old.png",
                "member@example.com",
                "회원"
        );
        member.updateProfileImage(
                "member-profiles/2026/10/27/profile-key",
                "image/png",
                5L
        );

        member.synchronizeExternalProfileImage(
                "https://example.com/new.png"
        );

        assertThat(member.getProfileImageUrl()).isNull();
        assertThat(member.getProfileImageStorageKey())
                .isEqualTo("member-profiles/2026/10/27/profile-key");
        assertThat(member.getProfileImageContentType())
                .isEqualTo("image/png");
        assertThat(member.getProfileImageSizeBytes()).isEqualTo(5L);
    }

    @Test
    void applyExternalProfileImageReplacesStoredImageState() {
        Member member = Member.create(
                "member@example.com",
                "회원"
        );
        member.updateProfileImage(
                "member-profiles/2026/10/27/profile-key",
                "image/png",
                5L
        );

        member.applyExternalProfileImage(
                "https://example.com/social.png"
        );

        assertThat(member.getProfileImageUrl())
                .isEqualTo("https://example.com/social.png");
        assertThat(member.getProfileImageStorageKey()).isNull();
        assertThat(member.getProfileImageContentType()).isNull();
        assertThat(member.getProfileImageSizeBytes()).isNull();
    }
}
