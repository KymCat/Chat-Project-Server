package com.project.ChatProject.repository;

import com.project.ChatProject.entity.ChatRoomMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {

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
}
