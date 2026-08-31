package com.project.ChatProject.entity;

import com.project.ChatProject.entity.base.BaseCreatedTimeEntity;
import com.project.ChatProject.entity.enums.ChatMessageType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "chat_messages",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_chat_messages_sender_id_client_message_id",
                        columnNames = {"sender_id", "client_message_id"}
                ),

                @UniqueConstraint(
                        name = "uq_chat_messages_attachment_id",
                        columnNames = "attachment_id"
                )
        },
        indexes = {
                @Index(
                        name = "idx_chat_messages_room_id_id_desc",
                        columnList = "room_id, id DESC"
                )
        }
)
public class ChatMessage extends BaseCreatedTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    private Member member;

    @Column(name = "client_message_id")
    private UUID clientMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type",
            nullable = false,
            length = 20)
    private ChatMessageType type;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attachment_id")
    private Attachment attachment;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
