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
import com.talkie.chat.user.enums.PreferredLanguage;
import com.talkie.chat.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

// 힌트: RoomErrorCode(MEMBER_IDS_REQUIRED, ROOM_NAME_REQUIRED, INVITEE_NOT_FOUND,
// NOT_ROOM_MEMBER, ROOM_NOT_FOUND)를 참고해 예외 케이스를 채우세요.
// createRoom의 memberCount 계산(roomMembers.size()+1)과 findAllById가 존재하지 않는
// ID를 조용히 스킵하는 특성은 과거 버그였던 지점이니 특히 신경써서 검증해보세요.
@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;
    @Mock
    private RoomMemberRepository roomMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MessageRepository messageRepository;

    private RoomService roomService;

    @BeforeEach
    void setUp() {
        roomService = new RoomService(roomRepository, roomMemberRepository, userRepository, messageRepository);
    }

    @Nested
    @DisplayName("채팅방 생성")
    class CreateRoom {

        @Test
        @DisplayName("memberIds가 비어있으면 MEMBER_IDS_REQUIRED")
        void createRoom_emptyMemberIds() {
            // TODO: given-when-then
            Long userId = 1L;
            String roomName = "test room";
            RoomType type = RoomType.DIRECT;
            List<Long> memberIds = new ArrayList<>();

            BusinessException exception =
                    assertThrows(BusinessException.class, () -> roomService.createRoom(userId, roomName, type, memberIds));
            assertThat(exception.getErrorCode()).isEqualTo(RoomErrorCode.MEMBER_IDS_REQUIRED);
        }

        @Test
        @DisplayName("GROUP 타입인데 roomName이 없으면 ROOM_NAME_REQUIRED")
        void createRoom_groupWithoutName() {
            // TODO: given-when-then
            Long userId = 1L;
            String roomName = "   ";
            RoomType type = RoomType.GROUP;
            List<Long> memberIds = List.of(2L, 3L);
            User user =
                    new User("Test", "password01", "test01@gmail.com", "Test01", null, PreferredLanguage.KO);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            BusinessException exception = assertThrows(BusinessException.class, () -> roomService.createRoom(userId, roomName, type, memberIds));
            assertThat(exception.getErrorCode()).isEqualTo(RoomErrorCode.ROOM_NAME_REQUIRED);
        }

        @Test
        @DisplayName("DIRECT 타입이고 roomName이 없으면 초대 대상의 닉네임으로 자동 설정된다")
        void createRoom_directAutoName() {
            // TODO: given-when-then
            Long userId = 1L;
            Long invitedId = 2L;
            String roomName = null;
            RoomType type = RoomType.DIRECT;
            List<Long> memberIds = List.of(invitedId);
            User user =
                    new User("Test", "password01", "test01@gmail.com", "Test01", null, PreferredLanguage.KO);
            User invitee =
                    new User("Kim", "pw", "invitee@gmail.com", "InviteeNick", null, PreferredLanguage.KO);
            ReflectionTestUtils.setField(invitee, "id", invitedId);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userRepository.findById(invitedId)).thenReturn(Optional.of(invitee));
            when(userRepository.findAllById(List.of(invitedId))).thenReturn(List.of(invitee));
            when(roomRepository.save(any(Room.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            RoomResponse response = roomService.createRoom(userId, roomName, type, memberIds);

            assertThat(response.roomName()).isEqualTo(invitee.getNickname());
        }

        @Test
        @DisplayName("존재하지 않는 초대 대상이 섞여 있으면 INVITEE_NOT_FOUND " +
                "(findAllById가 없는 ID를 조용히 제외하는 것을 size 비교로 잡아내는지 확인)")
        void createRoom_inviteeNotFound() {
            // TODO: given-when-then
            Long userId = 1L;
            Long existingId = 2L;
            Long missingId = 3L;
            String roomName = "test room";
            RoomType type = RoomType.GROUP;
            List<Long> memberIds = List.of(existingId, missingId);

            User user =
                    new User("Test", "password01", "test01@gmail.com", "Test01", null, PreferredLanguage.KO);
            User invitee =
                    new User("Kim", "pw", "invitee@gmail.com", "InviteeNick", null, PreferredLanguage.KO);
            ReflectionTestUtils.setField(invitee, "id", existingId);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(userRepository.findAllById(memberIds)).thenReturn(List.of(invitee));

            BusinessException exception =
                    assertThrows(BusinessException.class, () -> roomService.createRoom(userId, roomName, type, memberIds));
            assertThat(exception.getErrorCode()).isEqualTo(RoomErrorCode.INVITEE_NOT_FOUND);
        }

        @Test
        @DisplayName("memberIds에 방장 본인 ID가 섞여 있어도 memberCount가 정확히 계산된다 (중복 집계 방지)")
        void createRoom_memberCountExcludesDuplicateOwner() {
            // TODO: given-when-then
            Long userId = 1L;
            Long existingId = 2L;
            String roomName = "test room";
            RoomType type = RoomType.GROUP;
            List<Long> memberIds = List.of(userId, existingId);

            User user =
                    new User("Test", "password01", "test01@gmail.com", "Test01", null, PreferredLanguage.KO);
            User invitee =
                    new User("Kim", "pw", "invitee@gmail.com", "InviteeNick", null, PreferredLanguage.KO);
            ReflectionTestUtils.setField(user, "id", userId);
            ReflectionTestUtils.setField(invitee, "id", existingId);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(userRepository.findAllById(memberIds)).thenReturn(List.of(user, invitee));

            RoomResponse response = roomService.createRoom(userId, roomName, type, memberIds);

            assertThat(response.memberCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("memberIds에 중복 ID가 있으면 distinct 처리되어 한 번만 초대된다")
        void createRoom_distinctMemberIds() {
            // TODO: given-when-then
            Long userId = 1L;
            Long existingId = 2L;
            String roomName = "test room";
            RoomType type = RoomType.GROUP;
            List<Long> memberIds = List.of(existingId, existingId);

            User user =
                    new User("Test", "password01", "test01@gmail.com", "Test01", null, PreferredLanguage.KO);
            User invitee =
                    new User("Kim", "pw", "invitee@gmail.com", "InviteeNick", null, PreferredLanguage.KO);
            ReflectionTestUtils.setField(user, "id", userId);
            ReflectionTestUtils.setField(invitee, "id", existingId);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(userRepository.findAllById(List.of(existingId))).thenReturn(List.of(invitee));

            RoomResponse response = roomService.createRoom(userId, roomName, type, memberIds);

            assertThat(response.memberCount()).isEqualTo(2);
            verify(userRepository).findAllById(List.of(existingId));
        }
    }

    @Nested
    @DisplayName("채팅방 목록 조회")
    class GetMyRooms {

        @Test
        @DisplayName("가입한 방이 없으면 빈 목록을 반환한다")
        void getMyRooms_empty() {
            // TODO: given-when-then
            Long userId = 1L;

            when(roomMemberRepository.findByUserId(userId)).thenReturn(List.of());

            List<RoomSummaryResponse> myRooms = roomService.getMyRooms(userId);
            assertThat(myRooms.size()).isEqualTo(0);
        }

        @Test
        @DisplayName("방 목록과 함께 마지막 메시지, 안읽은 메시지 수, 멤버 수가 정확히 채워진다")
        void getMyRooms_success() {
            Long userId = 1L;
            Long roomId = 10L;
            Long lastReadMessageId = 5L;

            // 1. 방 멤버(RoomMember)가 참조할 Room 준비
            Room room = new Room("test room", RoomType.GROUP);
            ReflectionTestUtils.setField(room, "id", roomId);

            // 2. RoomMember 준비 (roomMemberRepository.findByUserId가 반환할 값)
            User user = new User("Test", "password01", "test01@gmail.com", "Test01", null, PreferredLanguage.KO);
            RoomMember myRoomMember = new RoomMember(Role.MEMBER, user, room);
            ReflectionTestUtils.setField(myRoomMember, "lastReadMessageId", lastReadMessageId);

            // 3. 마지막 메시지 (messageRepository.findFirstMessages가 반환할 값)
            Message lastMessage = new Message("hello", "client-msg-1", user, room);
            ReflectionTestUtils.setField(lastMessage, "createdAt", LocalDateTime.now());

            // TODO: mock 세팅
             when(roomMemberRepository.findByUserId(userId)).thenReturn(List.of(myRoomMember));
            when(roomMemberRepository.countMembersByRoomIdIn(List.of(roomId)))
                    .thenReturn(List.<Object[]>of(new Object[]{roomId, 3L}));
             when(messageRepository.findFirstMessages(roomId, 1)).thenReturn(List.of(lastMessage));
             when(messageRepository.countUnread(roomId, lastReadMessageId)).thenReturn(2L);

            // TODO: when
             List<RoomSummaryResponse> myRooms = roomService.getMyRooms(userId);

            // TODO: then - roomId, roomName, lastMessage, lastMessageAt, memberCount(3), unreadCount(2) 검증
            assertThat(myRooms.size()).isEqualTo(1);
            RoomSummaryResponse summary = myRooms.get(0);
            assertThat(summary.roomId()).isEqualTo(roomId);
            assertThat(summary.roomName()).isEqualTo(room.getRoomName());
            assertThat(summary.lastMessage()).isEqualTo(lastMessage.getContent());
            assertThat(summary.lastMessageAt()).isEqualTo(lastMessage.getCreatedAt());
            assertThat(summary.memberCount()).isEqualTo(3);
            assertThat(summary.unreadCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("채팅방 퇴장")
    class LeaveRoom {

        @Test
        @DisplayName("존재하지 않는 방이면 ROOM_NOT_FOUND")
        void leaveRoom_roomNotFound() {
            Long userId = 1L;
            Long roomId = 99L;

            // TODO: mock 세팅
             when(roomRepository.findById(roomId)).thenReturn(Optional.empty());

            // TODO: when-then
             BusinessException exception =
                     assertThrows(BusinessException.class, () -> roomService.leaveRoom(userId, roomId));
             assertThat(exception.getErrorCode()).isEqualTo(RoomErrorCode.ROOM_NOT_FOUND);
        }

        @Test
        @DisplayName("방 멤버가 아니면 NOT_ROOM_MEMBER")
        void leaveRoom_notMember() {
            Long userId = 1L;
            Long roomId = 10L;
            Room room = new Room("test room", RoomType.GROUP);
            ReflectionTestUtils.setField(room, "id", roomId);

            // TODO: mock 세팅
             when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
             when(roomMemberRepository.findByUserIdAndRoomId(userId, roomId)).thenReturn(Optional.empty());

            // TODO: when-then
             BusinessException exception =
                     assertThrows(BusinessException.class, () -> roomService.leaveRoom(userId, roomId));
             assertThat(exception.getErrorCode()).isEqualTo(RoomErrorCode.NOT_ROOM_MEMBER);
        }

        @Test
        @DisplayName("OWNER가 아닌 멤버가 퇴장하면 RoomMember만 삭제된다")
        void leaveRoom_memberLeaves() {
            Long userId = 1L;
            Long roomId = 10L;
            Room room = new Room("test room", RoomType.GROUP);
            ReflectionTestUtils.setField(room, "id", roomId);
            User user = new User("Test", "password01", "test01@gmail.com", "Test01", null, PreferredLanguage.KO);
            RoomMember roomMember = new RoomMember(Role.MEMBER, user, room);

            // TODO: mock 세팅
             when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
             when(roomMemberRepository.findByUserIdAndRoomId(userId, roomId)).thenReturn(Optional.of(roomMember));

            // TODO: when
             roomService.leaveRoom(userId, roomId);

            // TODO: then
            verify(roomMemberRepository).delete(roomMember);
            verify(roomMemberRepository, never()).findOldestMemberByRoomId(any());
        }

        @Test
        @DisplayName("OWNER가 퇴장하고 다른 멤버가 남아있으면 가장 오래된 멤버에게 소유권이 이전된다")
        void leaveRoom_ownerTransfersToOldestMember() {
            Long userId = 1L;
            Long roomId = 10L;
            Room room = new Room("test room", RoomType.GROUP);
            ReflectionTestUtils.setField(room, "id", roomId);
            User owner = new User("Test", "password01", "test01@gmail.com", "Test01", null, PreferredLanguage.KO);
            RoomMember ownerMember = new RoomMember(Role.OWNER, owner, room);
            User nextOwnerUser = new User("Kim", "pw", "invitee@gmail.com", "InviteeNick", null, PreferredLanguage.KO);
            RoomMember nextOwnerMember = new RoomMember(Role.MEMBER, nextOwnerUser, room);

            // TODO: mock 세팅
             when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
             when(roomMemberRepository.findByUserIdAndRoomId(userId, roomId)).thenReturn(Optional.of(ownerMember));
             when(roomMemberRepository.findOldestMemberByRoomId(roomId)).thenReturn(Optional.of(nextOwnerMember));

            // TODO: when
            roomService.leaveRoom(userId, roomId);

            // TODO: then
            // - nextOwnerMember.getRole()이 Role.OWNER로 바뀌었는지
            assertThat(nextOwnerMember.getRole()).isEqualTo(Role.OWNER);
            assertThat(room.getDeletedAt()).isNull();
            verify(roomMemberRepository).delete(ownerMember);
            // - room.delete()가 호출되지 않았는지 (room.getDeletedAt()이 null인지)
        }

        @Test
        @DisplayName("OWNER가 마지막 남은 멤버로 퇴장하면 방이 soft-delete된다")
        void leaveRoom_lastOwnerDeletesRoom() {
            Long userId = 1L;
            Long roomId = 10L;
            Room room = new Room("test room", RoomType.GROUP);
            ReflectionTestUtils.setField(room, "id", roomId);
            User owner = new User("Test", "password01", "test01@gmail.com", "Test01", null, PreferredLanguage.KO);
            RoomMember ownerMember = new RoomMember(Role.OWNER, owner, room);

            // TODO: mock 세팅
             when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
             when(roomMemberRepository.findByUserIdAndRoomId(userId, roomId)).thenReturn(Optional.of(ownerMember));
             when(roomMemberRepository.findOldestMemberByRoomId(roomId)).thenReturn(Optional.empty());

            // TODO: when
             roomService.leaveRoom(userId, roomId);

            // TODO: then
            verify(roomMemberRepository).delete(ownerMember);
            assertThat(room.getDeletedAt()).isNotNull();
        }
    }
}
