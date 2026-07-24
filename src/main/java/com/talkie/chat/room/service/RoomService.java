package com.talkie.chat.room.service;

import com.talkie.chat.message.repository.MessageRepository;
import com.talkie.chat.room.dto.RoomResponse;
import com.talkie.chat.room.dto.RoomSummaryResponse;
import com.talkie.chat.room.entity.Room;
import com.talkie.chat.room.entity.RoomMember;
import com.talkie.chat.room.enums.Role;
import com.talkie.chat.room.enums.RoomType;
import com.talkie.chat.room.repository.RoomMemberRepository;
import com.talkie.chat.room.repository.RoomRepository;
import com.talkie.chat.user.entity.User;
import com.talkie.chat.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("없는 유저입니다."));

        if (roomType == RoomType.GROUP) {
            if (roomName == null) {
                throw new IllegalArgumentException("그룹 채팅은 방 이름이 필수입니다.");
            }
        } else {
            if (roomName == null) {
                User inviteUser = userRepository.findById(memberIds.get(0))
                        .orElseThrow(() -> new IllegalArgumentException("없는 유저입니다."));
                roomName = inviteUser.getNickname();
            }
        }
        Room room = new Room(roomName, roomType);
        Room createdRoom = roomRepository.save(room);

        RoomMember roomOwner = new RoomMember(Role.OWNER, user, createdRoom);
        roomMemberRepository.save(roomOwner);

        List<Long> requestIds = memberIds.stream().distinct().toList();
        List<User> members = userRepository.findAllById(requestIds);
        if (members.size() != requestIds.size()) {
            throw new IllegalArgumentException("존재하지 않는 초대 대상이 있습니다.");
        }

        List<RoomMember> roomMembers = new ArrayList<>();
        for(User member : members) {
            if (member.getId().equals(userId)) {
                continue;
            }
            roomMembers.add(new RoomMember(Role.MEMBER, member, createdRoom));
        }
        roomMemberRepository.saveAll(roomMembers);
        return RoomResponse.from(createdRoom, roomMembers.size() + 1);
    }

    public List<RoomSummaryResponse> getMyRooms(Long userId) {
        return roomMemberRepository.findSummaryRoomsInform(userId)
                .stream()
                .map(row -> new RoomSummaryResponse(
                        (Long) row[0],
                        (String) row[1],
                        (String) row[2],
                        (LocalDateTime) row[3],
                        ((Long) row[4]).intValue(),
                        ((Long) row[5]).intValue()
                ))
                .toList();
    }

    @Transactional
    public void markAsRead(Long userId, Long roomId) {
        RoomMember roomMember = roomMemberRepository.findByUserIdAndRoomId(userId, roomId)
                .orElseThrow(() -> new IllegalArgumentException("해당 방의 회원이 아닙니다."));

        messageRepository.findLatestMessageIdByRoomId(roomId)
                .ifPresent(roomMember::updateLastReadMessageId);
    }

    @Transactional
    public void leaveRoom(Long userId, Long roomId) {
        Room room = findByRoomId(roomId);
        RoomMember roomMember = roomMemberRepository.findByUserIdAndRoomId(userId, room.getId())
                .orElseThrow(() -> new IllegalArgumentException("해당 방의 회원이 아닙니다."));

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
                .orElseThrow(() -> new IllegalArgumentException("방을 찾을 수 없습니다."));
    }
}
