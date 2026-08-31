package com.project.ChatProject.entity;

import com.project.ChatProject.entity.base.BaseCreatedTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "member_credentials")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberCredential extends BaseCreatedTimeEntity {

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "password_changed_at", nullable = false)
    private Instant passwordChangedAt;

    private MemberCredential(Member member, String passwordHash) {
        this.member = member;
        this.passwordHash = passwordHash;
        this.passwordChangedAt = Instant.now();
    }

    public static MemberCredential create(Member member, String passwordHash) {
        return new MemberCredential(member, passwordHash);
    }
}
