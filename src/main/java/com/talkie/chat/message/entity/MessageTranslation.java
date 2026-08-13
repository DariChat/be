package com.talkie.chat.message.entity;

import com.talkie.chat.user.enums.PreferredLanguage;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "message_translations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"message_id", "language"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageTranslation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PreferredLanguage language;

    @Column(nullable = false, length = 500)
    private String translatedContent;

    public MessageTranslation(Message message, PreferredLanguage language, String translatedContent) {
        this.message = message;
        this.language = language;
        this.translatedContent = translatedContent;
    }
}