package com.project.ChatProject.repository;

import com.project.ChatProject.entity.MemberCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberCredentialRepository extends JpaRepository<MemberCredential, Long> {

    @Query(
            value = """
                    SELECT mc.passwordHash
                    FROM MemberCredential mc
                    WHERE mc.memberId = :memberId
                    """)
    String getPasswordHashById(@Param("memberId") Long memberId);
}
