package com.project.ChatProject.entity;

import com.project.ChatProject.entity.base.BaseCreatedTimeEntity;
import com.project.ChatProject.entity.enums.AttachmentStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "attachments",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_attachments_storage_key",
                        columnNames = "storage_key"
                )
        }
)
public class Attachment extends BaseCreatedTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploader_id", nullable = false)
    private Member uploader;

    @Column(name = "original_name",
            nullable = false,
            length = 255)
    private String originalName;

    @Column(name = "content_type",
            nullable = false,
            length = 255)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "storage_key",
            nullable = false,
            length = 1024)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status",
            nullable = false,
            length = 20)
    private AttachmentStatus status;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    private Attachment(
            Member uploader,
            String originalName,
            String contentType,
            long sizeBytes,
            String storageKey
    ) {
        this.uploader = uploader;
        this.originalName = originalName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
        this.status = AttachmentStatus.PENDING;
    }

    public void activate() {
        if (status == AttachmentStatus.ACTIVE) {
            return;
        }

        if (status == AttachmentStatus.DELETED) {
            throw new IllegalStateException(
                    "삭제된 첨부파일은 활성화할 수 없습니다."
            );
        }

        this.status = AttachmentStatus.ACTIVE;
        this.activatedAt = Instant.now();
    }

    public void delete() {
        if (status == AttachmentStatus.DELETED) {
            return;
        }

        this.status = AttachmentStatus.DELETED;
        this.deletedAt = Instant.now();
    }

    public static Attachment createPending(
            Member uploader,
            String originalName,
            String contentType,
            long sizeBytes,
            String storageKey
    ) {
        return new Attachment(
                uploader,
                originalName,
                contentType,
                sizeBytes,
                storageKey
        );
    }
}
