package com.talkie.chat.user.repository;

import com.talkie.chat.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByNickname(String nickname);

    @Query("SELECT u FROM User u WHERE LOWER(u.nickname) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "AND u.id != :excludeUserId AND (:cursor IS NULL OR u.nickname > :cursor) " +
            "ORDER BY u.nickname ASC LIMIT :size")
    List<User> searchByNickname(@Param("keyword") String keyword,
                                 @Param("excludeUserId") Long excludeUserId,
                                 @Param("cursor") String cursor,
                                 @Param("size") int size);

    @Query(value = "SELECT u.* FROM users u WHERE u.id != :userId " +
            "AND u.preferred_language != :preferredLanguage " +
            "AND u.id NOT IN (:excludeIds) " +
            "AND u.id NOT IN (SELECT f.requester_id FROM friendships f WHERE f.addressee_id = :userId " +
            "UNION SELECT f.addressee_id FROM friendships f WHERE f.requester_id = :userId) " +
            "ORDER BY RAND() LIMIT :size", nativeQuery = true)
    List<User> findRecommendations(@Param("userId") Long userId,
                                    @Param("preferredLanguage") String preferredLanguage,
                                    @Param("excludeIds") List<Long> excludeIds,
                                    @Param("size") int size);
}
