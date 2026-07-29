package com.talkie.chat.message.repository;

import com.talkie.chat.message.entity.Message;
import com.talkie.chat.message.enums.PublishStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {
    /**
     * (created_at, id) 키셋 커서. created_at 단독 커서는 동일 시각(특히 초 단위로
     * 저장되는 datetime 컬럼)에 걸친 메시지를 다음 페이지에서 영구히 누락시킬 수 있어,
     * id를 타이브레이커로 추가했다. idx_message_room_created 인덱스에 id를 3번째
     * 컬럼으로 포함시켜, OR로 전개한 조건에서도 인덱스 정렬을 그대로 활용하도록 한다.
     */
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

