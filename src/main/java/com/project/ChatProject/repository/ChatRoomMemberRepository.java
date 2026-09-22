package com.project.ChatProject.repository;

import com.project.ChatProject.dto.projection.ChatRoomUnreadCountProjection;
import com.project.ChatProject.entity.ChatRoomMember;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {

    // 현재 Member가 속해있는 모든 채팅방 조회
    @Query(
            value =
                    """
                    SELECT crm
                    FROM ChatRoomMember crm
                    JOIN FETCH crm.chatRoom cr
                    WHERE crm.member.id = :memberId
                        AND crm.leftAt IS NULL
                        AND cr.deletedAt IS NULL
                    ORDER BY COALESCE(cr.lastMessageAt, cr.createdAt) DESC
                    """
    )
    List<ChatRoomMember> findAllActiveByMemberId(@Param("memberId") Long memberId);

    Optional<ChatRoomMember> findByChatRoomIdAndMemberId(
            Long roomId,
            Long memberId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            value = """
                    SELECT crm
                    FROM ChatRoomMember crm
                    WHERE crm.chatRoom.id = :roomId
                        AND crm.member.id = :memberId
                        AND crm.leftAt IS NULL
                    """
    )
    Optional<ChatRoomMember> findByChatRoomIdAndMemberIdForUpdate(
            @Param("roomId") Long roomId,
            @Param("memberId") Long memberId
    );

    // 참여가능한 모든 채팅방 조회
    @Query(
            value = """
                    SELECT crm
                    FROM ChatRoomMember crm
                    JOIN FETCH crm.member m
                    WHERE crm.chatRoom.id = :chatRoomId
                        AND crm.leftAt IS NULL
                    ORDER BY m.id DESC
                    """
    )
    List<ChatRoomMember> findAllParticipatingByChatRoomId(
            @Param("chatRoomId") Long chatRoomId
    );

    // 채팅방별 읽지 않은 메세지 반환
    @Query(
            value = """
                    SELECT crm.chatRoom.id As roomId,
                           COUNT(cm.id) AS unreadCount
                    FROM ChatRoomMember crm
                    LEFT JOIN crm.lastReadMessage lastRead
                    JOIN ChatMessage cm
                        ON crm.chatRoom = cm.chatRoom
                    WHERE crm.member.id = :memberId
                        AND crm.leftAt IS NULL
                        AND crm.chatRoom.deletedAt IS NULL
                        AND cm.createdAt >= crm.joinedAt
                        AND (lastRead.id IS NULL
                             OR cm.createdAt > lastRead.createdAt
                             OR (
                                 cm.createdAt = lastRead.createdAt
                                 AND cm.id > lastRead.id
                             )
                        )
                        AND (cm.sender IS NULL OR cm.sender.id <> :memberId)
                    GROUP BY crm.chatRoom.id
                    """
    )
    List<ChatRoomUnreadCountProjection> findUnreadCountsByMemberId(
            @Param("memberId") Long memberId
    );
}
