package com.project.ChatProject.repository;

import com.project.ChatProject.entity.Friendship;
import com.project.ChatProject.entity.enums.FriendshipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FriendshipRepository
        extends JpaRepository<Friendship, Long> {

    // 해당 상태(요청 or 친구)가 있는지 양방향 검사
    @Query("""
            SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END
            FROM Friendship f
            WHERE (
                (f.requester.id = :requesterId
                    AND f.receiver.id = :receiverId)
                OR
                (f.requester.id = :receiverId
                    AND f.receiver.id = :requesterId)
            )
            AND f.status = :status
            """)
    boolean existsBetweenMembersAndStatus(
            @Param("requesterId") Long requesterId,
            @Param("receiverId") Long receiverId,
            @Param("status") FriendshipStatus status
    );
}
