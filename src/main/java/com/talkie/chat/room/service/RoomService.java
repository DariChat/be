package com.talkie.chat.room.service;

import com.talkie.chat.global.exception.BusinessException;
import com.talkie.chat.message.entity.Message;
import com.talkie.chat.message.repository.MessageRepository;
import com.talkie.chat.room.dto.RoomResponse;
import com.talkie.chat.room.dto.RoomSummaryResponse;
import com.talkie.chat.room.entity.Room;
import com.talkie.chat.room.entity.RoomMember;
import com.talkie.chat.room.enums.Role;
import com.talkie.chat.room.enums.RoomType;
import com.talkie.chat.room.exception.RoomErrorCode;
import com.talkie.chat.room.repository.RoomMemberRepository;
import com.talkie.chat.room.repository.RoomRepository;
import com.talkie.chat.user.entity.User;
import com.talkie.chat.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;

    @Transactional
    public RoomResponse createRoom(Long userId, String roomName, RoomType roomType, List<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            throw new BusinessException(RoomErrorCode.MEMBER_IDS_REQUIRED);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(RoomErrorCode.USER_NOT_FOUND));

        if (roomType == RoomType.GROUP) {
            if (roomName == null || roomName.isBlank()) {
                throw new BusinessException(RoomErrorCode.ROOM_NAME_REQUIRED);
            }
            return createGroupRoom(user, roomName, memberIds);
        }
        return findOrCreateDirectRoom(user, memberIds.get(0));
    }

    private RoomResponse findOrCreateDirectRoom(User user, Long otherUserId) {
        Optional<Long> existingRoomId = roomMemberRepository.findDirectRoomIdBetween(user.getId(), otherUserId);
        if (existingRoomId.isPresent()) {
            Room existingRoom = findByRoomId(existingRoomId.get());
            User otherUser = userRepository.findById(otherUserId)
                    .orElseThrow(() -> new BusinessException(RoomErrorCode.USER_NOT_FOUND));
            return RoomResponse.of(existingRoom, otherUser.getNickname(), 2, true);
        }

        User otherUser = userRepository.findById(otherUserId)
                .orElseThrow(() -> new BusinessException(RoomErrorCode.USER_NOT_FOUND));

        Room room = new Room(null, RoomType.DIRECT);
        Room createdRoom = roomRepository.save(room);

        roomMemberRepository.save(new RoomMember(Role.OWNER, user, createdRoom));
        roomMemberRepository.save(new RoomMember(Role.MEMBER, otherUser, createdRoom));

        return RoomResponse.of(createdRoom, otherUser.getNickname(), 2, false);
    }

    private RoomResponse createGroupRoom(User user, String roomName, List<Long> memberIds) {
        Room room = new Room(roomName, RoomType.GROUP);
        Room createdRoom = roomRepository.save(room);

        RoomMember roomOwner = new RoomMember(Role.OWNER, user, createdRoom);
        roomMemberRepository.save(roomOwner);

        List<Long> requestIds = memberIds.stream().distinct().toList();
        List<User> members = userRepository.findAllById(requestIds);
        if (members.size() != requestIds.size()) {
            throw new BusinessException(RoomErrorCode.INVITEE_NOT_FOUND);
        }

        List<RoomMember> roomMembers = new ArrayList<>();
        for(User member : members) {
            if (member.getId().equals(user.getId())) {
                continue;
            }
            roomMembers.add(new RoomMember(Role.MEMBER, member, createdRoom));
        }
        roomMemberRepository.saveAll(roomMembers);
        return RoomResponse.of(createdRoom, createdRoom.getRoomName(), roomMembers.size() + 1, false);
    }

    public List<RoomSummaryResponse> getMyRooms(Long userId) {
        List<RoomMember> myRoomMembers = roomMemberRepository.findByUserId(userId);
        if (myRoomMembers.isEmpty()) {
            return List.of();
        }

        List<Long> roomIds = myRoomMembers.stream().map(rm -> rm.getRoom().getId()).toList();
        Map<Long, Long> memberCountByRoomId = roomMemberRepository.countMembersByRoomIdIn(roomIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        Map<Long, String> directRoomNameByRoomId = resolveDirectRoomNames(userId, myRoomMembers);

        return myRoomMembers.stream()
                .map(rm -> toSummary(rm, memberCountByRoomId.getOrDefault(rm.getRoom().getId(), 0L), directRoomNameByRoomId))
                .toList();
    }

    private Map<Long, String> resolveDirectRoomNames(Long userId, List<RoomMember> myRoomMembers) {
        List<Long> directRoomIds = myRoomMembers.stream()
                .filter(rm -> rm.getRoom().getRoomType() == RoomType.DIRECT)
                .map(rm -> rm.getRoom().getId())
                .toList();
        if (directRoomIds.isEmpty()) {
            return Map.of();
        }

        return roomMemberRepository.findByRoomIdIn(directRoomIds).stream()
                .filter(rm -> !rm.getUser().getId().equals(userId))
                .collect(Collectors.toMap(rm -> rm.getRoom().getId(), rm -> rm.getUser().getNickname(), (a, b) -> a));
    }

    private RoomSummaryResponse toSummary(RoomMember myRoomMember, long memberCount, Map<Long, String> directRoomNameByRoomId) {
        Long roomId = myRoomMember.getRoom().getId();
        Optional<Message> lastMessage = messageRepository.findFirstMessages(roomId, 1).stream().findFirst();
        long unreadCount = messageRepository.countUnread(roomId, myRoomMember.getLastReadMessageId());

        String roomName = myRoomMember.getRoom().getRoomType() == RoomType.DIRECT
                ? directRoomNameByRoomId.get(roomId)
                : myRoomMember.getRoom().getRoomName();

        return new RoomSummaryResponse(
                roomId,
                roomName,
                lastMessage.map(Message::getContent).orElse(null),
                lastMessage.map(Message::getCreatedAt).orElse(null),
                (int) memberCount,
                (int) unreadCount
        );
    }

    @Transactional
    public void markAsRead(Long userId, Long roomId) {
        if (!roomMemberRepository.existsByUserIdAndRoomId(userId, roomId)) {
            throw new BusinessException(RoomErrorCode.NOT_ROOM_MEMBER);
        }

        messageRepository.findLatestMessageIdByRoomId(roomId)
                .ifPresent(messageId -> roomMemberRepository.updateLastReadMessageIdIfGreater(userId, roomId, messageId));
    }

    @Transactional
    public void markAsRead(Set<Long> userIds, Long roomId, Long messageId) {
        if (userIds.isEmpty()) {
            return;
        }
        roomMemberRepository.updateLastReadMessageIdIfGreater(userIds, roomId, messageId);
    }

    @Transactional
    public void leaveRoom(Long userId, Long roomId) {
        Room room = findByRoomId(roomId);
        RoomMember roomMember = roomMemberRepository.findByUserIdAndRoomId(userId, room.getId())
                .orElseThrow(() -> new BusinessException(RoomErrorCode.NOT_ROOM_MEMBER));

        if (roomMember.getRole() == Role.OWNER) {
            Optional<RoomMember> nextOwner = roomMemberRepository.findOldestMemberByRoomId(roomId);
            if (nextOwner.isPresent()) {
                nextOwner.get().changeRole(Role.OWNER);
            } else {
                room.delete();
            }
        }
        roomMemberRepository.delete(roomMember);
    }

    private Room findByRoomId(Long roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(RoomErrorCode.ROOM_NOT_FOUND));
    }
}
