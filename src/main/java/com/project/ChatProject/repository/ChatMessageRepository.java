package com.project.ChatProject.repository;

import com.project.ChatProject.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository
        extends JpaRepository<ChatMessage, Long>
{
    @Query(
            value = """
                    SELECT cm
                    FROM ChatMessage cm
                    WHERE cm.chatRoom.id = :chatRoomId
                        AND cm.createdAt >= :joinedAt
                    ORDER BY cm.createdAt DESC, cm.id DESC
                    """
    )
    List<ChatMessage> findLatestMessages(
            @Param("chatRoomId") Long chatRoomId,
            @Param("joinedAt") Instant joinedAt,
            Pageable pageable
    );

    @Query(
            value = """
                    SELECT cm
                    FROM ChatMessage cm
                    WHERE cm.chatRoom.id = :chatRoomId
                        AND cm.createdAt >= :joinedAt
                        AND (cm.createdAt < :beforeCreatedAt
                            OR (cm.createdAt = :beforeCreatedAt
                                AND cm.id < :beforeId
                            )
                        )
                    ORDER BY cm.createdAt DESC, cm.id DESC
                    """
    )
    List<ChatMessage> findMessages(
            @Param("chatRoomId") Long chatRoomId,
            @Param("beforeId") Long beforeMessageId,
            @Param("beforeCreatedAt") Instant beforeCreatedAt,
            @Param("joinedAt") Instant joinedAt,
            Pageable pageable
    );

    Optional<ChatMessage> findByIdAndChatRoomId(
            Long messageId,
            Long chatRoomId
    );
}
