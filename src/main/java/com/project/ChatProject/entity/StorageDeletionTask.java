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
        name = "storage_deletion_tasks",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_storage_deletion_tasks_storage_key",
                        columnNames = "storage_key"
                )
        }
)
public class StorageDeletionTask extends BaseCreatedTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "storage_key",
            nullable = false,
            length = 1024
    )
    private String storageKey;

    private StorageDeletionTask(String storageKey) {
        this.storageKey = storageKey;
    }

    public static StorageDeletionTask create(String storageKey) {
        return new StorageDeletionTask(storageKey);
    }
}
