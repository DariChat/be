package com.talkie.chat.message.repository;

import com.talkie.chat.message.entity.Message;
import com.talkie.chat.message.enums.PublishStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {
    @Query("SELECT m FROM Message m JOIN FETCH m.user WHERE m.room.id = :roomId AND m.id < :cursor AND m.deletedAt IS NULL ORDER BY m.id DESC LIMIT :size")
    List<Message> findMessages(@Param("roomId") Long roomId, @Param("cursor") Long cursor, @Param("size") int size);
    @Query("SELECT m FROM Message m JOIN FETCH m.user WHERE m.room.id = :roomId AND m.deletedAt IS NULL ORDER BY m.id DESC LIMIT :size")
    List<Message> findFirstMessages(@Param("roomId") Long roomId, @Param("size") int size);
    @Query("SELECT m.id FROM Message m WHERE m.room.id = :roomId AND m.deletedAt IS NULL ORDER BY m.id DESC LIMIT 1")
    Optional<Long> findLatestMessageIdByRoomId(@Param("roomId") Long roomId);
    Optional<Message> findByClientMessageId(String clientMessageId);

    /**
     * fromStatuses에 해당할 때만 newStatus로 전이한다. 동시에 여러 요청이 같은
     * 메시지를 발행하려 해도 이 UPDATE로 단 하나만 영향받은 행(1)을 얻어 발행
     * 권한을 원자적으로 선점하게 한다. 나머지는 0을 받아 발행을 건너뛴다.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Message m SET m.publishStatus = :newStatus " +
            "WHERE m.id = :id AND m.publishStatus IN :fromStatuses")
    int updateStatusIfIn(@Param("id") Long id,
                          @Param("newStatus") PublishStatus newStatus,
                          @Param("fromStatuses") Collection<PublishStatus> fromStatuses);
}

