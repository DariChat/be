package com.talkie.chat.message.repository;

import com.talkie.chat.message.entity.MessageTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MessageTranslationRepository extends JpaRepository<MessageTranslation, Long> {
    @Query("SELECT mt FROM MessageTranslation mt WHERE mt.message.id IN :messageIds")
    List<MessageTranslation> findByMessageIdIn(@Param("messageIds") Collection<Long> messageIds);

    List<MessageTranslation> findByMessageId(Long messageId);
}