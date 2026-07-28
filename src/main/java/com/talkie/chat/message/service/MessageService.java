package com.talkie.chat.message.service;

import com.talkie.chat.global.exception.BusinessException;
import com.talkie.chat.message.dto.MessageResponse;
import com.talkie.chat.message.entity.Message;
import com.talkie.chat.message.enums.PublishStatus;
import com.talkie.chat.message.exception.MessageErrorCode;
import com.talkie.chat.message.repository.MessageRepository;
import com.talkie.chat.room.entity.RoomMember;
import com.talkie.chat.room.repository.RoomMemberRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    private final MessageRepository messageRepository;
    private final RoomMemberRepository roomMemberRepository;

    /**
     * clientMessageId로 멱등하게 저장한다. 동일한 clientMessageId로 재요청이 오면
     * (예: Redis 발행 실패 후 클라이언트 재시도) 새로 저장하지 않고 기존 메시지를 반환한다.
     * 기존 메시지를 재사용할 때도 멤버십과 소유자/방 일치를 검증해, 남의
     * clientMessageId를 재사용해 비멤버 방으로 메시지를 재발행시키는 것을 막는다.
     */
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
        return MessageResponse.from(existing);
    }

    private MessageResponse persistNewMessage(Long userId, Long roomId, String content, String clientMessageId) {
        RoomMember roomMember = roomMemberRepository.findByUserIdAndRoomId(userId, roomId)
                .orElseThrow(() -> new BusinessException(MessageErrorCode.NOT_ROOM_MEMBER));

        Message message = new Message(content, clientMessageId, roomMember.getUser(), roomMember.getRoom());
        Message savedMessage = messageRepository.save(message);
        roomMemberRepository.updateLastReadMessageIdIfGreater(userId, roomId, savedMessage.getId());
        return MessageResponse.from(savedMessage);
    }

    /**
     * PENDING/FAILED 상태일 때만 PUBLISHING으로 원자적으로 전이해 발행 권한을
     * 선점한다. 동시에 같은 clientMessageId로 재요청이 겹쳐도 이 UPDATE에서
     * 단 하나의 요청만 true를 받아 실제로 Redis에 발행하게 되고, 나머지는
     * false를 받아 발행을 건너뛴다.
     */
    @Transactional
    public boolean tryStartPublishing(Long messageId) {
        int updated = messageRepository.updateStatusIfIn(
                messageId, PublishStatus.PUBLISHING, EnumSet.of(PublishStatus.PENDING, PublishStatus.FAILED));
        return updated > 0;
    }

    /**
     * Redis publish는 이미 성공한 뒤 호출되므로 여기서 실패해도 재발행으로
     * 이어지면 안 된다. DB 순단 등 일시적 장애를 흡수하기 위해 짧게 재시도하고,
     * 그래도 실패하면 recoverMarkPublished에서 PUBLISHING을 FAILED로 되돌려
     * 다음 클라이언트 재시도가 다시 발행을 시도할 수 있게 한다.
     */
    @Transactional
    @Retryable(retryFor = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 200, multiplier = 2))
    public void markPublished(Long messageId) {
        messageRepository.findById(messageId).ifPresent(Message::markPublished);
    }

    /**
     * markPublishFailed를 그대로 호출하지 않고 별도 트랜잭션을 직접 여는 이유:
     * @Recover 메서드는 같은 빈 안에서의 self-invocation으로 markPublishFailed를
     * 부르게 되는데, 그러면 프록시를 거치지 않아 @Transactional이 적용되지 않고
     * 변경사항이 커밋되지 않은 채 조용히 유실된다.
     */
    @Recover
    @Transactional
    public void recoverMarkPublished(Exception e, Long messageId) {
        log.error("발행은 성공했으나 publishStatus=PUBLISHED 기록에 재시도 후에도 실패했습니다. " +
                "PUBLISHING 락을 풀기 위해 FAILED로 되돌립니다. messageId={}", messageId, e);
        messageRepository.findById(messageId).ifPresent(Message::markPublishFailed);
    }

    @Transactional
    public void markPublishFailed(Long messageId) {
        messageRepository.findById(messageId).ifPresent(Message::markPublishFailed);
    }

    public List<MessageResponse> findMessagesByRoomId(Long userId, Long roomId, LocalDateTime cursor, int size) {
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
