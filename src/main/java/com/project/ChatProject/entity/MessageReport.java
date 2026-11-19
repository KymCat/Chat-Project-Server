package com.project.ChatProject.entity;

import com.project.ChatProject.entity.base.BaseCreatedTimeEntity;
import com.project.ChatProject.entity.enums.ChatMessageType;
import com.project.ChatProject.entity.enums.MessageReportReason;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "message_reports",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_message_reports_reporter_id_message_id",
                        columnNames = {"reporter_id, message_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_message_reports_created_at_id_desc",
                        columnList = "created_at DESC, id DESC"
                ),

                @Index(
                        name = "idx_message_reports_reported_member_id",
                        columnList = "reported_member_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageReport extends BaseCreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "reason",
            length = 30,
            nullable = false
    )
    private MessageReportReason reason;

    @Column(
            name = "details",
            length = 500
    )
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "message_type_snapshot",
            length = 20,
            nullable = false
    )
    private ChatMessageType messageTypeSnapshot;

    @Column(
            name = "message_content_snapshot",
            columnDefinition = "TEXT"
    )
    private String messageContentSnapshot;

    // FK
    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "message_id",
            nullable = false
    )
    private ChatMessage message;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "reporter_id",
            nullable = false
    )
    private Member reporter;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "reported_member_id",
            nullable = false
    )
    private Member reportedMember;
}
