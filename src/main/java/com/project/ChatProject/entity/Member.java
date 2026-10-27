package com.project.ChatProject.entity;

import com.project.ChatProject.entity.enums.MemberStatus;
import com.project.ChatProject.entity.base.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "members")
public class Member extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "email",
            nullable = false,
            length = 254
    )
    private String email;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(
            name = "nickname",
            nullable = false,
            length = 30
    )
    private String nickname;

    @Column(name = "profile_image_url", length = 2048)
    private String profileImageUrl;

    @Column(
            name = "profile_image_storage_key",
            length = 1024,
            nullable = true
    )
    private String profileImageStorageKey;

    @Column(
            name = "profile_image_content_type",
            length = 100,
            nullable = true
    )
    private String profileImageContentType;

    @Column(name = "profile_image_size_bytes", nullable = true)
    private Long profileImageSizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status",
            nullable = false,
            length = 20)
    private MemberStatus status;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    private static Member createMember(
            String profileImageUrl,
            String email,
            String nickname
    ) {
        Member member = new Member();

        member.profileImageUrl = profileImageUrl;
        member.profileImageStorageKey = null;
        member.profileImageContentType = null;
        member.profileImageSizeBytes = null;
        member.email = email;
        member.nickname = nickname;
        member.emailVerifiedAt = null;
        member.status = MemberStatus.ACTIVE;
        member.lastLoginAt = null;
        member.withdrawnAt = null;

        return member;
    }

    public static Member create(
            String email,
            String nickname
    ) {
        return createMember(
                null,
                email,
                nickname
        );
    }

    public static Member createWithExternalProfileImage(
            String profileImageUrl,
            String email,
            String nickname
    ) {
        return createMember(
                profileImageUrl,
                email,
                nickname
        );
    }

    public void updateLastLoginAt() {
        this.lastLoginAt = Instant.now();
    }

    public void verifyEmail() {
        this.emailVerifiedAt = Instant.now();
    }

    public void updateProfileImage(
            String profileImageStorageKey,
            String profileImageContentType,
            long profileImageSizeBytes
    ) {
        this.profileImageUrl = null;
        this.profileImageStorageKey = profileImageStorageKey;
        this.profileImageContentType = profileImageContentType;
        this.profileImageSizeBytes = profileImageSizeBytes;
    }

    public void deleteProfileImage() {
        this.profileImageUrl = null;
        this.profileImageStorageKey = null;
        this.profileImageContentType = null;
        this.profileImageSizeBytes = null;
    }

    public boolean hasStoredProfileImage() {
        return this.profileImageStorageKey != null;
    }

    public void synchronizeExternalProfileImage(String profileImageUrl) {
        if (hasStoredProfileImage()) {
            return;
        }

        this.profileImageUrl = profileImageUrl;
    }

    public void applyExternalProfileImage(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
        this.profileImageStorageKey = null;
        this.profileImageContentType = null;
        this.profileImageSizeBytes = null;
    }
}
