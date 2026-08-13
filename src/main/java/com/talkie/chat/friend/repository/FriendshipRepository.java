package com.talkie.chat.friend.repository;

import com.talkie.chat.friend.entity.Friendship;
import com.talkie.chat.friend.enums.FriendshipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {
    boolean existsByRequesterIdAndAddresseeId(Long requesterId, Long addresseeId);

    Optional<Friendship> findByRequesterIdAndAddresseeId(Long requesterId, Long addresseeId);

    @Query("SELECT f FROM Friendship f JOIN FETCH f.requester WHERE f.addressee.id = :addresseeId AND f.status = :status")
    List<Friendship> findByAddresseeIdAndStatus(@Param("addresseeId") Long addresseeId,
                                                 @Param("status") FriendshipStatus status);

    @Query("SELECT f FROM Friendship f JOIN FETCH f.requester JOIN FETCH f.addressee " +
            "WHERE f.status = 'ACCEPTED' AND (f.requester.id = :userId OR f.addressee.id = :userId)")
    List<Friendship> findAcceptedByUserId(@Param("userId") Long userId);
}