package com.talkie.chat.message.service;

import com.talkie.chat.global.exception.BusinessException;
import com.talkie.chat.message.client.RetryableTranslationClient;
import com.talkie.chat.message.dto.MessageCursor;
import com.talkie.chat.message.dto.MessageResponse;
import com.talkie.chat.message.entity.Message;
import com.talkie.chat.message.entity.MessageTranslation;
import com.talkie.chat.message.exception.MessageErrorCode;
import com.talkie.chat.message.exception.TranslationException;
import com.talkie.chat.message.repository.MessageRepository;
import com.talkie.chat.message.repository.MessageTranslationRepository;
import com.talkie.chat.room.entity.RoomMember;
import com.talkie.chat.room.repository.RoomMemberRepository;
import com.talkie.chat.user.enums.PreferredLanguage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    private final MessageRepository messageRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final MessageTranslationRepository messageTranslationRepository;
    private final RetryableTranslationClient retryableTranslationClient;

    @Transactional
    public MessageResponse saveMessage(Long userId, Long roomId, String content, String clientMessageId) {
        if (!roomMemberRepository.existsByUserIdAndRoomId(userId, roomId)) {
            throw new BusinessException(MessageErrorCode.NOT_ROOM_MEMBER);
        }

        return messageRepository.findByClientMessageId(clientMessageId)
                .map(existing -> toResponseIfOwnedBy(existing, userId, roomId))
                .orElseGet(() -> persistNewMessage(userId, roomId, content, clientMessageId));
    }

    private MessageResponse toResponseIfOwnedBy(Message existing, Long userId, Long roomId) {
        if (!existing.getUser().getId().equals(userId) || !existing.getRoom().getId().equals(roomId)) {
            throw new BusinessException(MessageErrorCode.CLIENT_MESSAGE_ID_CONFLICT);
        }
        List<MessageTranslation> translations = messageTranslationRepository.findByMessageId(existing.getId());
        return MessageResponse.from(existing, translations);
    }

    private MessageResponse persistNewMessage(Long userId, Long roomId, String content, String clientMessageId) {
        RoomMember roomMember = roomMemberRepository.findByUserIdAndRoomId(userId, roomId)
                .orElseThrow(() -> new BusinessException(MessageErrorCode.NOT_ROOM_MEMBER));

        Message message = new Message(content, clientMessageId, roomMember.getUser(), roomMember.getRoom());
        Message savedMessage = messageRepository.save(message);
        roomMemberRepository.updateLastReadMessageIdIfGreater(userId, roomId, savedMessage.getId());

        List<MessageTranslation> translations = translateForRoomMembers(savedMessage, roomId, roomMember.getUser().getPreferredLanguage());
        return MessageResponse.from(savedMessage, translations);
    }

    private List<MessageTranslation> translateForRoomMembers(Message message, Long roomId, PreferredLanguage senderLanguage) {
        Set<PreferredLanguage> targetLanguages = roomMemberRepository.findByRoomId(roomId).stream()
                .map(rm -> rm.getUser().getPreferredLanguage())
                .filter(language -> language != senderLanguage)
                .collect(Collectors.toSet());

        List<MessageTranslation> translations = targetLanguages.stream()
                .map(language -> translateOrNull(message, language))
                .filter(Objects::nonNull)
                .toList();

        return messageTranslationRepository.saveAll(translations);
    }

    private MessageTranslation translateOrNull(Message message, PreferredLanguage targetLanguage) {
        try {
            String translatedContent = retryableTranslationClient.translate(message.getContent(), targetLanguage);
            return new MessageTranslation(message, targetLanguage, translatedContent);
        } catch (TranslationException e) {
            log.error("번역 실패로 원문만 전달합니다. messageId={}, targetLanguage={}", message.getId(), targetLanguage, e);
            return null;
        }
    }

    @Transactional
    @Retryable(retryFor = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 200, multiplier = 2))
    public void markPublished(Long messageId) {
        messageRepository.findById(messageId).ifPresent(Message::markPublished);
    }

    @Recover
    @Transactional
    public void recoverMarkPublished(Exception e, Long messageId) {
        log.error("발행은 성공했으나 publishStatus=PUBLISHED 기록에 재시도 후에도 실패했습니다. " +
                "FAILED로 기록해 다음 재시도가 다시 발행을 시도할 수 있게 합니다. messageId={}", messageId, e);
        messageRepository.findById(messageId).ifPresent(Message::markPublishFailed);
    }

    @Transactional
    public void markPublishFailed(Long messageId) {
        messageRepository.findById(messageId).ifPresent(Message::markPublishFailed);
    }

    public List<MessageResponse> findMessagesByRoomId(Long userId, Long roomId, MessageCursor cursor, int size) {
        if (!roomMemberRepository.existsByUserIdAndRoomId(userId, roomId)) {
            throw new BusinessException(MessageErrorCode.NOT_ROOM_MEMBER);
        }

        List<Message> findMessages;
        if (cursor == null) {
            findMessages = messageRepository.findFirstMessages(roomId, size);
        } else {
            findMessages = messageRepository.findMessages(roomId, cursor.createdAt(), cursor.id(), size);
        }

        List<Long> messageIds = findMessages.stream().map(Message::getId).toList();
        Map<Long, List<MessageTranslation>> translationsByMessageId = messageTranslationRepository.findByMessageIdIn(messageIds).stream()
                .collect(Collectors.groupingBy(mt -> mt.getMessage().getId()));

        return findMessages.stream()
                .map(message -> MessageResponse.from(message, translationsByMessageId.getOrDefault(message.getId(), List.of())))
                .toList();
    }
}
