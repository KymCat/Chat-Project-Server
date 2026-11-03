package com.project.ChatProject.entity;

import com.project.ChatProject.entity.base.BaseTimeEntity;
import com.project.ChatProject.entity.enums.ChatRoomType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "chat_rooms",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_chat_rooms_direct_key",
                        columnNames = "direct_key"
                )
        }
)
public class ChatRoom extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20, nullable = false)
    private ChatRoomType type;

    @Column(name = "name", length = 30)
    private String name;

    @Column(name = "direct_key", length = 50)
    private String directKey;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    private ChatRoom(
            ChatRoomType type,
            String name,
            String directKey
    ) {
        this.type = type;
        this.name = name;
        this.directKey = directKey;
        this.lastMessageAt = null;
        this.deletedAt = null;
    }

    public static ChatRoom createGroup(String name) {
        return new ChatRoom(
                ChatRoomType.GROUP,
                name,
                null
        );
    }

    public static ChatRoom createDirect(String directKey) {
        return new ChatRoom(
                ChatRoomType.DIRECT,
                null,
                directKey
        );
    }

    public void updateLastMessageAt(Instant messageCreatedAt) {
        this.lastMessageAt = messageCreatedAt;
    }

    public void updateName(String name) {
        this.name = name;
    }

    public void delete() {
        if (deletedAt == null)
            this.deletedAt = Instant.now();
    }
}
