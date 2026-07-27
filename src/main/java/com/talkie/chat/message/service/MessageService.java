package com.talkie.chat.message.service;

import com.talkie.chat.global.exception.BusinessException;
import com.talkie.chat.message.dto.MessageResponse;
import com.talkie.chat.message.entity.Message;
import com.talkie.chat.message.exception.MessageErrorCode;
import com.talkie.chat.message.repository.MessageRepository;
import com.talkie.chat.room.entity.RoomMember;
import com.talkie.chat.room.repository.RoomMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageService {

    private final MessageRepository messageRepository;
    private final RoomMemberRepository roomMemberRepository;

    /**
     * clientMessageId로 멱등하게 저장한다. 동일한 clientMessageId로 재요청이 오면
     * (예: Redis 발행 실패 후 클라이언트 재시도) 새로 저장하지 않고 기존 메시지를 반환한다.
     */
    @Transactional
    public MessageResponse saveMessage(Long userId, Long roomId, String content, String clientMessageId) {
        return messageRepository.findByClientMessageId(clientMessageId)
                .map(MessageResponse::from)
                .orElseGet(() -> persistNewMessage(userId, roomId, content, clientMessageId));
    }

    private MessageResponse persistNewMessage(Long userId, Long roomId, String content, String clientMessageId) {
        RoomMember roomMember = roomMemberRepository.findByUserIdAndRoomId(userId, roomId)
                .orElseThrow(() -> new BusinessException(MessageErrorCode.NOT_ROOM_MEMBER));

        Message message = new Message(content, clientMessageId, roomMember.getUser(), roomMember.getRoom());
        Message savedMessage = messageRepository.save(message);
        roomMemberRepository.updateLastReadMessageIdIfGreater(userId, roomId, savedMessage.getId());
        return MessageResponse.from(savedMessage);
    }

    @Transactional
    public void markPublished(Long messageId) {
        messageRepository.findById(messageId).ifPresent(Message::markPublished);
    }

    @Transactional
    public void markPublishFailed(Long messageId) {
        messageRepository.findById(messageId).ifPresent(Message::markPublishFailed);
    }

    public List<MessageResponse> findMessagesByRoomId(Long userId, Long roomId, Long cursor, int size) {
        if (!roomMemberRepository.existsByUserIdAndRoomId(userId, roomId)) {
            throw new BusinessException(MessageErrorCode.NOT_ROOM_MEMBER);
        }

        List<Message> findMessages;
        if (cursor == null) {
            findMessages = messageRepository.findFirstMessages(roomId, size);
        } else {
            findMessages = messageRepository.findMessages(roomId, cursor, size);
        }

        return findMessages.stream()
                .map(MessageResponse::from)
                .toList();
    }
}
