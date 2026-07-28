package com.talkie.chat.message.entity;

import com.talkie.chat.message.enums.PublishStatus;
import com.talkie.chat.room.entity.Room;
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
@Table(name = "message", indexes = {
        // 방별 최신 메시지 목록 조회(room_id 필터 + created_at 정렬)를 커버하기 위한 복합 인덱스.
        // room_id 단독 인덱스는 필터링 후 정렬(filesort)이 별도로 발생해, 이 인덱스로 대체한다.
        @Index(name = "idx_message_room_created", columnList = "room_id, created_at DESC")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Message {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String content;
    @Column(nullable = false, unique = true, length = 100)
    private String clientMessageId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PublishStatus publishStatus;
    @CreatedDate
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    public Message(String content, String clientMessageId, User user, Room room) {
        this.content = content;
        this.clientMessageId = clientMessageId;
        this.publishStatus = PublishStatus.PENDING;
        this.user = user;
        this.room = room;
    }

    public void deleteMessage() {
        this.deletedAt = LocalDateTime.now();
    }

    public void markPublished() {
        this.publishStatus = PublishStatus.PUBLISHED;
    }

    public void markPublishFailed() {
        this.publishStatus = PublishStatus.FAILED;
    }
}
