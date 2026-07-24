package com.talkie.chat.room.entity;

import com.talkie.chat.room.enums.Role;
import com.talkie.chat.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "room_members")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomMember {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedDate
    private LocalDateTime joinedAt;
    @Enumerated(EnumType.STRING)
    private Role role;

    private Long lastReadMessageId;
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "room_id")
    private Room room;

    public RoomMember(Role role, User user, Room room) {
        this.role = role;
        this.user = user;
        this.room = room;
    }

    public void updateLastReadMessageId(Long messageId) {
        if (this.lastReadMessageId == null || messageId > this.lastReadMessageId) {
            this.lastReadMessageId = messageId;
        }
    }
    public void changeRole(Role role) {
        this.role = role;
    }
}
