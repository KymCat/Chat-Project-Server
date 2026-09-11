package com.project.ChatProject.repository;

import com.project.ChatProject.entity.ChatRoom;
import com.project.ChatProject.entity.enums.ChatRoomType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatRoomRepository
        extends JpaRepository<ChatRoom, Long>
{
    @Query(
            value = """
                    SELECT cr
                    FROM ChatRoom cr
                    WHERE cr.deletedAt IS NULL
                        AND cr.type = :type
                        AND NOT EXISTS (
                            SELECT crm
                            FROM ChatRoomMember crm
                            WHERE crm.chatRoom = cr
                                AND crm.member.id = :memberId
                                AND crm.leftAt IS NULL
                            )
                    ORDER BY COALESCE(cr.lastMessageAt, cr.createdAt) DESC
                    """
    )
    List<ChatRoom> findAllGroupChatRoom(
            @Param("memberId") Long memberId,
            @Param("type") ChatRoomType type
    );
}
