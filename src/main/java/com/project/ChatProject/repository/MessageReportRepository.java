package com.project.ChatProject.repository;

import com.project.ChatProject.entity.MessageReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MessageReportRepository
        extends JpaRepository<MessageReport, Long> {

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
}
