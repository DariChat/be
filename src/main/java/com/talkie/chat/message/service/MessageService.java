package com.talkie.chat.message.service;

import com.talkie.chat.message.dto.MessageResponse;
import com.talkie.chat.message.entity.Message;
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

    @Transactional
    public MessageResponse saveMessage(Long userId, Long roomId, String content) {
        RoomMember roomMember = roomMemberRepository.findByUserIdAndRoomId(userId, roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방 멤버가 아닙니다."));

        Message message = new Message(content, roomMember.getUser(), roomMember.getRoom());
        Message savedMessage = messageRepository.save(message);
        roomMember.updateLastReadMessageId(savedMessage.getId());
        return MessageResponse.from(savedMessage);
    }

    public List<MessageResponse> findMessagesByRoomId(Long userId, Long roomId, Long cursor, int size) {
        if (!roomMemberRepository.existsByUserIdAndRoomId(userId, roomId)) {
            throw new IllegalArgumentException("채팅방 멤버가 아닙니다.");
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
