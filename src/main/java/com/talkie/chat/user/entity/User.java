package com.talkie.chat.user.entity;

import com.talkie.chat.user.enums.PreferredLanguage;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false, length = 100)
    private String password;
    @Column(nullable = false, unique = true, length = 100)
    private String email;
    @Column(nullable = false, unique = true, length = 20)
    private String nickname;
    @Column(length = 500)
    private String profileImageUrl;
    private LocalDateTime lastActiveAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PreferredLanguage preferredLanguage;

    @CreatedDate
    private LocalDateTime createdAt;

    public User(String name, String password,
                String email, String nickname, String profileImageUrl,
                PreferredLanguage preferredLanguage) {
        this.name = name;
        this.password = password;
        this.email = email;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.preferredLanguage = preferredLanguage;
        this.lastActiveAt = LocalDateTime.now();
    }

    public void updateProfile(String nickname, String profileImageUrl, PreferredLanguage preferredLanguage) {
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.preferredLanguage = preferredLanguage;
    }

    public void updatePassword(String password) {
        this.password = password;
    }

    public void updateLastActiveAt() {
        this.lastActiveAt = LocalDateTime.now();
    }
}
