package com.project.ChatProject.repository;

import com.project.ChatProject.entity.ChatMessage;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
                        AND NOT EXISTS (
                            SELECT mb.id
                            FROM MemberBlock mb
                            WHERE mb.blocked.id = :viewerId
                                AND (mb.blocker = cm.sender OR
                                     mb.blocker = cm.relatedMember)
                        )
                    ORDER BY cm.createdAt DESC, cm.id DESC
                    """
    )
    List<ChatMessage> findLatestMessages(
            @Param("chatRoomId") Long chatRoomId,
            @Param("joinedAt") Instant joinedAt,
            @Param("viewerId") Long viewerId,
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
                        AND NOT EXISTS (
                            SELECT mb.id
                            FROM MemberBlock mb
                            WHERE mb.blocked.id = :viewerId
                                AND (mb.blocker = cm.sender OR
                                     mb.blocker = cm.relatedMember)
                        )
                    ORDER BY cm.createdAt DESC, cm.id DESC
                    """
    )
    List<ChatMessage> findMessages(
            @Param("chatRoomId") Long chatRoomId,
            @Param("beforeId") Long beforeMessageId,
            @Param("beforeCreatedAt") Instant beforeCreatedAt,
            @Param("viewerId") Long viewerId,
            @Param("joinedAt") Instant joinedAt,
            Pageable pageable
    );

    @Query(
            value = """
                    SELECT cm
                    FROM ChatMessage cm
                    WHERE cm.id = :id
                        AND cm.chatRoom.id = :chatRoomId
                        AND NOT EXISTS (
                            SELECT mb.id
                            FROM MemberBlock mb
                            WHERE mb.blocked.id = :viewerId
                                AND (mb.blocker = cm.sender OR
                                     mb.blocker = cm.relatedMember)
                        )
                    """
    )
    Optional<ChatMessage> findVisibleByIdAndChatRoomId(
            @Param("id") Long messageId,
            @Param("chatRoomId") Long chatRoomId,
            @Param("viewerId") Long viewerId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            value = """
                    SELECT cm
                    FROM ChatMessage cm
                    WHERE cm.id = :id
                        AND cm.chatRoom.id = :roomId
                    """
    )
    Optional<ChatMessage> findByIdAndRoomIdForUpdate(
            @Param("id") Long id,
            @Param("roomId") Long roomId
            );


    @Query(
            value = """
                    SELECT cm
                    FROM ChatMessage cm
                    JOIN FETCH cm.attachment a
                    WHERE cm.chatRoom.id = :roomId
                        AND a.id = :attachmentId
                        AND cm.deletedAt IS NULL
                        AND NOT EXISTS (
                            SELECT mb.id
                            FROM MemberBlock mb
                            WHERE mb.blocked.id = :viewerId
                              AND (
                                  mb.blocker = cm.sender
                                  OR mb.blocker = cm.relatedMember
                              )
                        )
                    """
    )
    Optional<ChatMessage> findActiveMessageByRoomIdAndAttachmentId(
            @Param("roomId") Long roomId,
            @Param("attachmentId") Long attachmentId,
            @Param("viewerId") Long viewerId
    );

    // 채팅메세지 최초 검색
    @Query(
            value = """
                    SELECT cm
                    FROM ChatMessage cm
                    JOIN FETCH cm.sender
                    WHERE cm.chatRoom.id = :roomId
                        AND cm.createdAt >= :joinedAt
                        AND cm.type =
                            com.project.ChatProject.entity.enums.ChatMessageType.TEXT
                        AND cm.deletedAt IS NULL
                        AND LOCATE(LOWER(:keyword), LOWER(cm.content)) > 0
                        AND NOT EXISTS (
                            SELECT mb.id
                            FROM MemberBlock mb
                            WHERE mb.blocked.id = :viewerId
                                AND (mb.blocker = cm.sender OR
                                     mb.blocker = cm.relatedMember)
                        )
                    ORDER BY cm.createdAt DESC, cm.id DESC
                    """
    )
    List<ChatMessage> searchMessage(
            @Param("roomId") Long roomId,
            @Param("keyword") String keyword,
            @Param("joinedAt") Instant joinedAt,
            @Param("viewerId") Long viewerId,
            Pageable pageable
    );

    // 채팅 메세지 커서 검색
    @Query(
            value = """
                    SELECT cm
                    FROM ChatMessage cm
                    JOIN FETCH cm.sender
                    WHERE cm.chatRoom.id = :roomId
                        AND cm.createdAt >= :joinedAt
                        AND cm.type =
                            com.project.ChatProject.entity.enums.ChatMessageType.TEXT
                        AND cm.deletedAt IS NULL
                        AND (
                            cm.createdAt < :beforeCreatedAt
                            OR (
                                cm.createdAt = :beforeCreatedAt
                                AND cm.id < :beforeMessageId
                            )
                        )
                        AND NOT EXISTS (
                            SELECT mb.id
                            FROM MemberBlock mb
                            WHERE mb.blocked.id = :viewerId
                                AND (mb.blocker = cm.sender OR
                                     mb.blocker = cm.relatedMember)
                        )
                        AND LOCATE(LOWER(:keyword), LOWER(cm.content)) > 0
                    ORDER BY cm.createdAt DESC, cm.id DESC
                    """
    )
    List<ChatMessage> searchMessageBefore(
            @Param("roomId") Long roomId,
            @Param("keyword") String keyword,
            @Param("joinedAt") Instant joinedAt,
            @Param("beforeCreatedAt") Instant beforeCreatedAt,
            @Param("beforeMessageId") Long beforeMessageId,
            @Param("viewerId") Long viewerId,
            Pageable pageable
    );

}
