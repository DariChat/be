package com.talkie.chat.room.repository;

import com.talkie.chat.room.entity.RoomMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {
    @Query("SELECT rm.room.id, rm.room.roomName, " +
            "(SELECT m.content FROM Message m WHERE m.id = (SELECT MAX(m2.id) FROM Message m2 WHERE m2.room.id = rm.room.id AND m2.deletedAt IS NULL))," +
            "(SELECT m.createdAt FROM Message m WHERE m.id = (SELECT MAX(m2.id) FROM Message m2 WHERE m2.room.id = rm.room.id AND m2.deletedAt IS NULL))," +
            "(SELECT COUNT(rm2) FROM RoomMember rm2 WHERE rm2.room.id = rm.room.id)," +
            "(SELECT COUNT(m3) FROM Message m3 WHERE m3.room.id = rm.room.id AND m3.deletedAt IS NULL AND (rm.lastReadMessageId IS NULL OR m3.id > rm.lastReadMessageId)) " +
            "FROM RoomMember rm WHERE rm.user.id = :userId")
    List<Object[]> findSummaryRoomsInform(@Param("userId") Long userId);
    @Query("SELECT rm FROM RoomMember rm WHERE rm.user.id = :userId AND rm.room.id = :roomId")
    Optional<RoomMember> findByUserIdAndRoomId(@Param("userId") Long userId, @Param("roomId") Long roomId);

    @Query("SELECT COUNT(rm) > 0 FROM RoomMember rm WHERE rm.user.id = :userId AND rm.room.id = :roomId")
    boolean existsByUserIdAndRoomId(@Param("userId") Long userId, @Param("roomId") Long roomId);

    @Query("SELECT rm FROM RoomMember rm WHERE rm.room.id = :roomId AND rm.role = 'MEMBER' ORDER BY rm.joinedAt ASC LIMIT 1")
    Optional<RoomMember> findOldestMemberByRoomId(@Param("roomId") Long roomId);

    @Query("SELECT rm FROM RoomMember rm WHERE rm.user.id IN :userIds AND rm.room.id = :roomId")
    List<RoomMember> findByUserIdInAndRoomId(@Param("userIds") Set<Long> userIds, @Param("roomId") Long roomId);
}
