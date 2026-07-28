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
     * 정렬/커서 기준을 created_at 단독으로 맞춰 (room_id, created_at) 복합 인덱스를
     * filesort 없이 그대로 탄다. 처음엔 (createdAt, id) 튜플을 커서로 써서 동시간대
     * 메시지까지 안정적으로 구분하려 했으나, MySQL이 OR/튜플 비교 조건에서는 인덱스가
     * 보장하는 정렬 순서를 신뢰하지 못해 filesort로 되돌아가 버렸다 (id 정렬 때와
     * 동일한 문제가 재발). LocalDateTime이 마이크로초 정밀도라 실사용에서 동시간대
     * 충돌 가능성은 무시할 수준이라 판단해 created_at 단독 커서로 단순화했다.
     */
    @Query("SELECT m FROM Message m JOIN FETCH m.user WHERE m.room.id = :roomId AND m.deletedAt IS NULL " +
            "AND m.createdAt < :cursor ORDER BY m.createdAt DESC LIMIT :size")
    List<Message> findMessages(@Param("roomId") Long roomId, @Param("cursor") LocalDateTime cursor, @Param("size") int size);
    @Query("SELECT m FROM Message m JOIN FETCH m.user WHERE m.room.id = :roomId AND m.deletedAt IS NULL ORDER BY m.createdAt DESC LIMIT :size")
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

