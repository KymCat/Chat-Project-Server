package com.project.ChatProject.entity;

import com.project.ChatProject.entity.base.BaseCreatedTimeEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "administrators")
public class Administrators extends BaseCreatedTimeEntity {

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    private Administrators(Member member) {
        this.member = member;
    }

    public static Administrators create(Member member) {
        return new Administrators(member);
    }
}
