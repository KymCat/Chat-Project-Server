package com.project.ChatProject.entity;

import com.project.ChatProject.entity.base.BaseTimeEntity;
import com.project.ChatProject.entity.enums.SocialProvider;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "social_accounts",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_social_accounts_provider_provider_user_id",
                        columnNames = {"provider", "provider_user_id"}
                ),

                @UniqueConstraint(
                        name = "uq_social_accounts_member_id_provider",
                        columnNames = {"member_id", "provider"}
                )
        }
)
public class SocialAccount extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private SocialProvider provider;

    @Column(name = "provider_user_id",
            nullable = false,
            length = 255)
    private String providerUserId;

    @Column(name = "provider_email", length = 254)
    private String providerEmail;

    @Column(name = "provider_display_name", length = 100)
    private String providerDisplayName;

    @Column(name = "provider_profile_image_url", length = 2048)
    private String providerProfileImageUrl;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;
}
