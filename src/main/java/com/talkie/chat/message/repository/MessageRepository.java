package com.talkie.chat.message.repository;

import com.talkie.chat.message.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    interface LastMessageProjection {
        Long getRoomId();
        String getContent();
        LocalDateTime getCreatedAt();
    }

    interface UnreadCountProjection {
        Long getRoomId();
        Long getUnreadCount();
    }
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

    @Query(value = "SELECT rm.room_id AS roomId, COUNT(m.id) AS unreadCount " +
            "FROM room_members rm " +
            "LEFT JOIN message m ON m.room_id = rm.room_id AND m.deleted_at IS NULL " +
            "AND (rm.last_read_message_id IS NULL OR m.id > rm.last_read_message_id) " +
            "WHERE rm.user_id = :userId AND rm.room_id IN :roomIds " +
            "GROUP BY rm.room_id", nativeQuery = true)
    List<UnreadCountProjection> countUnreadByRoomIdIn(@Param("userId") Long userId, @Param("roomIds") Collection<Long> roomIds);

    @Query(value = "SELECT room_id AS roomId, content AS content, created_at AS createdAt FROM ( " +
            "  SELECT room_id, content, created_at, " +
            "         ROW_NUMBER() OVER (PARTITION BY room_id ORDER BY created_at DESC, id DESC) AS rn " +
            "  FROM message " +
            "  WHERE room_id IN :roomIds AND deleted_at IS NULL " +
            ") ranked WHERE rn = 1", nativeQuery = true)
    List<LastMessageProjection> findLastMessagesByRoomIdIn(@Param("roomIds") Collection<Long> roomIds);
}

