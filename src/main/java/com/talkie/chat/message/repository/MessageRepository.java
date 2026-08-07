package com.talkie.chat.message.repository;

import com.talkie.chat.message.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {
    @Query("SELECT m FROM Message m JOIN FETCH m.user WHERE m.room.id = :roomId AND m.deletedAt IS NULL " +
            "AND (m.createdAt < :cursorCreatedAt OR (m.createdAt = :cursorCreatedAt AND m.id < :cursorId)) " +
            "ORDER BY m.createdAt DESC, m.id DESC LIMIT :size")
    List<Message> findMessages(@Param("roomId") Long roomId,
                                @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
                                @Param("cursorId") Long cursorId,
                                @Param("size") int size);
    @Query("SELECT m FROM Message m JOIN FETCH m.user WHERE m.room.id = :roomId AND m.deletedAt IS NULL " +
            "ORDER BY m.createdAt DESC, m.id DESC LIMIT :size")
    List<Message> findFirstMessages(@Param("roomId") Long roomId, @Param("size") int size);
    @Query("SELECT m.id FROM Message m WHERE m.room.id = :roomId AND m.deletedAt IS NULL ORDER BY m.createdAt DESC LIMIT 1")
    Optional<Long> findLatestMessageIdByRoomId(@Param("roomId") Long roomId);
    Optional<Message> findByClientMessageId(String clientMessageId);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.room.id = :roomId AND m.deletedAt IS NULL " +
            "AND (:lastReadMessageId IS NULL OR m.id > :lastReadMessageId)")
    long countUnread(@Param("roomId") Long roomId, @Param("lastReadMessageId") Long lastReadMessageId);
}

