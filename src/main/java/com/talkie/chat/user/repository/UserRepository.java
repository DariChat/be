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
}
