package com.project.ChatProject.repository;

import com.project.ChatProject.entity.StorageDeletionTask;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.awt.print.Pageable;
import java.util.List;
import java.util.Optional;

public interface StorageDeletionTaskRepository
        extends JpaRepository<StorageDeletionTask, Long> {

    /**
     * 여러 스케줄러가 같은 작업을 동시에 처리하지 않기 위한 LOCK
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
           SELECT task
           FROM StorageDeletionTask task
           WHERE task.id = :taskId
           """
    )
    Optional<StorageDeletionTask> findByIdForUpdate(
            @Param("taskId") Long taskId
    );

    @Query(
            """
            SELECT task.id
            FROM StorageDeletionTask task
            ORDER BY task.id ASC
            """
    )
    List<Long> findDeletionCandidateIds(Pageable pageable);
}
