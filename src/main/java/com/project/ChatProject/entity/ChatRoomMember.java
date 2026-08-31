package com.project.ChatProject.entity;

import com.project.ChatProject.entity.enums.ChatRoomMemberRole;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "chat_room_members",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_chat_room_members_room_id_member_id",
                        columnNames = {"room_id", "member_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_chat_room_members_member_id_left_at",
                        columnList = "member_id, left_at"
                ),
                @Index(
                        name = "idx_chat_room_members_room_id_left_at",
                        columnList = "room_id, left_at"
                )
        }
)
public class ChatRoomMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "role",
            nullable = false,
            length = 20)
    private ChatRoomMemberRole role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_read_message_id")
    private ChatMessage lastReadMessage;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;
}
