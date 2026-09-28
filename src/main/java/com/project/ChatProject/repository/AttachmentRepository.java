package com.project.ChatProject.repository;

import com.project.ChatProject.entity.Attachment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AttachmentRepository
        extends JpaRepository<Attachment, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            value = """
                    SELECT a
                    FROM Attachment a
                    WHERE a.id = :attachmentId
                    """
    )
    Optional<Attachment> findByForUpdate(
            @Param("attachmentId") Long attachmentId
    );
}
