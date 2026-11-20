package com.project.ChatProject.repository;

import com.project.ChatProject.entity.MessageReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MessageReportRepository
        extends JpaRepository<MessageReport, Long> {

    // 모든 신고내역 조회(페이지)
    @Query(
            value = """
                    SELECT mr
                    FROM MessageReport mr
                    JOIN FETCH mr.reporter
                    JOIN FETCH mr.reportedMember
                    JOIN FETCH mr.message
                    ORDER BY mr.createdAt DESC, mr.id DESC
                    """,
            countQuery =
                    """
                    SELECT COUNT(mr)
                    FROM MessageReport mr
                    """
    )
    Page<MessageReport> findAllMessageReport(Pageable pageable);

    // 신고 상세 조회
    @Query(
            value = """
                    SELECT mr
                    FROM MessageReport mr
                    JOIN FETCH mr.reporter
                    JOIN FETCH mr.reportedMember
                    JOIN FETCH mr.message m
                    LEFT JOIN FETCH m.attachment
                    WHERE mr.id = :reportId
                    """
    )
    Optional<MessageReport> findDetailById(
            @Param("reportId") Long reportId
    );

    // 신고자가 해당 메세지를 이미 신고했는지 확인
    boolean existsByReporterIdAndMessageId(
            Long reporterId,
            Long messageId
    );
}
