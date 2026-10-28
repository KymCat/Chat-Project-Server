package com.project.ChatProject.entity;

import com.project.ChatProject.entity.base.BaseCreatedTimeEntity;
import com.project.ChatProject.entity.enums.FriendshipStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "friendships"
)
public class Friendship extends BaseCreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private Member requester;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_id", nullable = false)
    private Member receiver;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 20
    )
    private FriendshipStatus status;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    private Friendship(
            Member requester,
            Member receiver
    ) {
        this.requester = requester;
        this.receiver = receiver;
        this.status = FriendshipStatus.PENDING;
        this.acceptedAt = null;
    }

    public static Friendship create(
            Member requester,
            Member receiver
    ) {
        return new Friendship(requester, receiver);
    }

    public void acceptRequest() {
        if (acceptedAt == null) {
            this.status = FriendshipStatus.ACCEPTED;
            this.acceptedAt = Instant.now();
        }
    }
}
