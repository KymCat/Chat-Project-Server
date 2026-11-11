package com.project.ChatProject.entity;

import com.project.ChatProject.entity.base.BaseCreatedTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "member_blocks",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_member_blocks_blocker_id_blocked_id",
                        columnNames = {"blocker_id", "blocked_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_member_blocks_blocked_id_blocker_id",
                        columnList = "blocked_id, blocker_id"
                )
        }
)
public class MemberBlock extends BaseCreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocker_id", nullable = false)
    private Member blocker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocked_id", nullable = false)
    private Member blocked;

    private MemberBlock(
            Member blocker,
            Member blocked
    ) {
        this.blocker = blocker;
        this.blocked = blocked;
    }

    public static MemberBlock create(
            Member blocker,
            Member blocked
    ) {
        return new MemberBlock(blocker, blocked);
    }
}
