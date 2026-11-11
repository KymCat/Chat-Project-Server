package com.project.ChatProject.repository;

import com.project.ChatProject.entity.MemberBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MemberBlockRepository
        extends JpaRepository<MemberBlock, Long> {

    boolean existsByBlockerIdAndBlockedId(
            Long blockerId,
            Long blockedId
    );

    Optional<MemberBlock> findByBlockerIdAndBlockedId(
            Long blockerId,
            Long blockedId
    );

    @Query("""
            SELECT mb
            FROM MemberBlock mb
            JOIN FETCH mb.blocked
            WHERE mb.blocker.id = :blockerId
            ORDER BY mb.id DESC
            """)
    List<MemberBlock> findAllByBlockerId(
            @Param("blockerId") Long blockerId
    );
}
