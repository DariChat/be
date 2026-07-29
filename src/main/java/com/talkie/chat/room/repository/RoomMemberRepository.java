package com.talkie.chat.room.repository;

import com.talkie.chat.room.entity.RoomMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {
    @Query("SELECT rm FROM RoomMember rm JOIN FETCH rm.room WHERE rm.user.id = :userId")
    List<RoomMember> findByUserId(@Param("userId") Long userId);

    @Query("SELECT rm.room.id, COUNT(rm) FROM RoomMember rm WHERE rm.room.id IN :roomIds GROUP BY rm.room.id")
    List<Object[]> countMembersByRoomIdIn(@Param("roomIds") Collection<Long> roomIds);

    @Query("SELECT rm FROM RoomMember rm WHERE rm.user.id = :userId AND rm.room.id = :roomId")
    Optional<RoomMember> findByUserIdAndRoomId(@Param("userId") Long userId, @Param("roomId") Long roomId);

    @Query("SELECT COUNT(rm) > 0 FROM RoomMember rm WHERE rm.user.id = :userId AND rm.room.id = :roomId")
    boolean existsByUserIdAndRoomId(@Param("userId") Long userId, @Param("roomId") Long roomId);

    @Query("SELECT rm FROM RoomMember rm WHERE rm.room.id = :roomId AND rm.role = 'MEMBER' ORDER BY rm.joinedAt ASC LIMIT 1")
    Optional<RoomMember> findOldestMemberByRoomId(@Param("roomId") Long roomId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE RoomMember rm SET rm.lastReadMessageId = :messageId " +
            "WHERE rm.user.id = :userId AND rm.room.id = :roomId " +
            "AND (rm.lastReadMessageId IS NULL OR rm.lastReadMessageId < :messageId)")
    int updateLastReadMessageIdIfGreater(@Param("userId") Long userId, @Param("roomId") Long roomId, @Param("messageId") Long messageId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE RoomMember rm SET rm.lastReadMessageId = :messageId " +
            "WHERE rm.user.id IN :userIds AND rm.room.id = :roomId " +
            "AND (rm.lastReadMessageId IS NULL OR rm.lastReadMessageId < :messageId)")
    int updateLastReadMessageIdIfGreater(@Param("userIds") Set<Long> userIds, @Param("roomId") Long roomId, @Param("messageId") Long messageId);
}
