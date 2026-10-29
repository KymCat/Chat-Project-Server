package com.project.ChatProject.repository;

import com.project.ChatProject.entity.Friendship;
import com.project.ChatProject.entity.enums.FriendshipStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

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

    // 보낸 요청 리스트 조회 (최초 조회)
    @Query(
            value = """
                    SELECT f
                    FROM Friendship f
                    JOIN Fetch f.receiver
                    WHERE f.requester.id = :memberId
                        AND f.status = :status
                    ORDER By f.id DESC
                    """
    )
    List<Friendship> findSentRequests(
            @Param("memberId") Long memberId,
            @Param("status") FriendshipStatus status,
            Pageable pageable
    );

    // 보낸 요청 리스트 조회
    @Query(
            value = """
                    SELECT f
                    FROM Friendship f
                    JOIN Fetch f.receiver
                    WHERE f.id < :beforeFriendshipId
                        AND f.requester.id = :memberId
                        AND f.status = :status
                    ORDER By f.id DESC
                    """
    )
    List<Friendship> findSentRequestsBefore(
            @Param("beforeFriendshipId") Long id,
            @Param("memberId") Long memberId,
            @Param("status") FriendshipStatus status,
            Pageable pageable
    );

    // 받은 요청 리스트 조회 (최초 조회 시)
    @Query(
            value = """
                    SELECT f
                    FROM Friendship f
                    JOIN Fetch f.requester
                    WHERE f.receiver.id = :memberId
                        AND f.status = :status
                    ORDER By f.id DESC
                    """
    )
    List<Friendship> findReceivedRequests(
            @Param("memberId") Long memberId,
            @Param("status") FriendshipStatus status,
            Pageable pageable
    );

    // 받은 요청 리스트 조회
    @Query(
            value = """
                    SELECT f
                    FROM Friendship f
                    JOIN Fetch f.requester
                    WHERE f.id < :beforeFriendshipId
                        AND f.receiver.id = :memberId
                        AND f.status = :status
                    ORDER By f.id DESC
                    """
    )
    List<Friendship> findReceivedRequestsBefore(
            @Param("beforeFriendshipId") Long id,
            @Param("memberId") Long memberId,
            @Param("status") FriendshipStatus status,
            Pageable pageable
    );
}
