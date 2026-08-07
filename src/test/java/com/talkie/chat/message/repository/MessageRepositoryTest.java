package com.talkie.chat.message.repository;

import com.talkie.chat.message.entity.Message;
import com.talkie.chat.room.entity.Room;
import com.talkie.chat.room.enums.RoomType;
import com.talkie.chat.room.repository.RoomRepository;
import com.talkie.chat.user.entity.User;
import com.talkie.chat.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

// 힌트: @DataJpaTest는 기본적으로 내장 H2로 대체되어 동작합니다(build.gradle에 h2 testRuntimeOnly 추가됨).
// Spring Boot 4.1부터 DataJpaTest/TestEntityManager가 spring-boot-data-jpa-test /
// spring-boot-jpa-test 모듈의 새 패키지로 이동했으니 다른 예제 코드의 import 경로를
// 그대로 믿지 마세요. TestEntityManager로 persist+flush해 테스트 데이터를 만들거나,
// repository.save() + flush()를 조합하세요. 가장 중요한 시나리오는 findMessages의
// (createdAt, id) 키셋 커서 — 같은 createdAt을 가진 메시지가 여러 개 있을 때 id로
// 정확히 다음 페이지를 잘라오는지입니다.
@DataJpaTest
@ActiveProfiles("test")
class MessageRepositoryTest {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Room room;
    private User user;

    @BeforeEach
    void setUp() {
        room = roomRepository.save(new Room("test room", RoomType.GROUP));
        user = userRepository.save(new User("Test", "password01", "test01@gmail.com", "Test01", null));
    }

    // Message는 @CreatedDate라 저장 시점의 시간이 자동으로 찍힘.
    // createdAt을 원하는 값으로 고정하고 싶으면 persist 후 entityManager로 직접 덮어써야 함.
    private Message persistMessage(String content, String clientMessageId, LocalDateTime createdAt) {
        Message message = new Message(content, clientMessageId, user, room);
        Message saved = entityManager.persistAndFlush(message);
        if (createdAt != null) {
            // TODO: 필요하면 ReflectionTestUtils.setField(saved, "createdAt", createdAt) 후 flush
            ReflectionTestUtils.setField(saved, "createdAt", createdAt);
        }
        return saved;
    }

    @Nested
    @DisplayName("findFirstMessages")
    class FindFirstMessages {

        @Test
        @DisplayName("최신순(createdAt DESC, id DESC)으로 정렬되어 반환된다")
        void findFirstMessages_orderedByLatest() {
            // TODO: given - persistMessage로 메시지 2~3개를 순서대로 저장
            // (같은 트랜잭션 내에서는 System 시간 해상도상 createdAt이 같을 수 있으니
            //  id 순서로 저장되는 것만으로도 충분히 검증 가능)
            Message first = persistMessage("first", "client-1", null);
            Message second = persistMessage("second", "client-2", null);
            Message third = persistMessage("third", "client-3", null);

            // TODO: when
            List<Message> result = messageRepository.findFirstMessages(room.getId(), 10);

            // TODO: then - result가 최신 저장분부터 내림차순인지 (id 기준 역순)
            assertThat(result.get(0).getContent()).isEqualTo("third");
            assertThat(result.get(1).getContent()).isEqualTo("second");
            assertThat(result.get(2).getContent()).isEqualTo("first");
        }

        @Test
        @DisplayName("size만큼만 잘라서 반환한다")
        void findFirstMessages_limitBySize() {
            // TODO: given - 메시지 3개 이상 저장
            Message first = persistMessage("first", "client-1", null);
            Message second = persistMessage("second", "client-2", null);
            Message third = persistMessage("third", "client-3", null);
            // TODO: when
            List<Message> result = messageRepository.findFirstMessages(room.getId(), 2);

            // TODO: then - result.size()가 2인지
            assertThat(result.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("삭제된(deletedAt IS NOT NULL) 메시지는 결과에서 제외된다")
        void findFirstMessages_excludesDeleted() {
            // TODO: given - 메시지 저장 후 deleteMessage() 호출하고 flush
            Message alive = persistMessage("alive", "client-1", null);
            Message deleted = persistMessage("deleted", "client-2", null);
            deleted.deleteMessage();
            entityManager.flush();
            // TODO: when
            List<Message> result = messageRepository.findFirstMessages(room.getId(), 10);

            // TODO: then - result에 삭제된 메시지가 없는지 (또는 빈 리스트인지)
            assertThat(result.size()).isEqualTo(1);
            assertThat(result.get(0).getContent()).isEqualTo("alive");
        }
    }

    @Nested
    @DisplayName("findMessages (커서 페이지네이션)")
    class FindMessages {

        @Test
        @DisplayName("커서 이전 메시지들만 최신순으로 반환한다")
        void findMessages_beforeCursor() {
            // TODO: given - 메시지 여러 개 저장, 그 중 하나를 커서로 사용
            Message first = persistMessage("first", "client-1", null);
            Message second = persistMessage("second", "client-2", null);
            Message third = persistMessage("third", "client-3", null);
            // TODO: when
            List<Message> result = messageRepository.findMessages(
                     room.getId(), second.getCreatedAt(), second.getId(), 10);

            // TODO: then - 커서보다 이전(더 과거) 메시지만 담겼는지
            assertThat(result.size()).isEqualTo(1);
            assertThat(result.get(0).getContent()).isEqualTo("first");
        }

        @Test
        @DisplayName("같은 createdAt을 가진 메시지가 여러 개 있어도 id를 타이브레이커로 " +
                "정확히 다음 페이지를 가져온다 (핵심 시나리오)")
        void findMessages_tieBreakByIdOnSameCreatedAt() {
            // TODO: given - persistMessage로 여러 메시지를 저장한 뒤,
            // ReflectionTestUtils로 createdAt을 전부 동일한 값으로 맞추고 flush
            // (id는 순차 증가하므로 자연히 타이브레이커 역할을 함)
            LocalDateTime fixedTime = LocalDateTime.now();
            Message first = persistMessage("first", "client-1", fixedTime);
            Message second = persistMessage("second", "client-2", fixedTime);
            Message third = persistMessage("third", "client-3", fixedTime);
            // TODO: when - 가장 큰 id를 커서로 findMessages 호출
            List<Message> result = messageRepository.findMessages(
                    room.getId(), third.getCreatedAt(), third.getId(), 10);

            // TODO: then - id가 커서보다 작은 메시지만, id 내림차순으로 반환되는지
            assertThat(result.size()).isEqualTo(2);
            assertThat(result.get(0).getContent()).isEqualTo("second");
            assertThat(result.get(1).getContent()).isEqualTo("first");
        }
    }

    @Nested
    @DisplayName("countUnread")
    class CountUnread {

        @Test
        @DisplayName("lastReadMessageId보다 큰 id의 메시지 개수만 센다")
        void countUnread_countsAfterLastRead() {
            // TODO: given - 메시지 여러 개 저장, 중간 id를 lastReadMessageId로 사용
            Message first = persistMessage("first", "client-1", null);
            Message second = persistMessage("second", "client-2", null);
            Message third = persistMessage("third", "client-3", null);
            Long lastReadMessageId = second.getId();
            // TODO: when
            long count = messageRepository.countUnread(room.getId(), lastReadMessageId);

            // TODO: then - lastReadMessageId보다 큰 id의 개수와 일치하는지
            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("lastReadMessageId가 null이면 전체 메시지 수를 센다")
        void countUnread_nullLastRead() {
            // TODO: given - 메시지 여러 개 저장
            Message first = persistMessage("first", "client-1", null);
            Message second = persistMessage("second", "client-2", null);
            Message third = persistMessage("third", "client-3", null);
            // TODO: when
            long count = messageRepository.countUnread(room.getId(), null);

            // TODO: then - 저장한 전체 메시지 수와 일치하는지
            assertThat(count).isEqualTo(3);
        }

        @Test
        @DisplayName("삭제된 메시지는 카운트에서 제외된다")
        void countUnread_excludesDeleted() {
            // TODO: given - 메시지 저장 후 일부 deleteMessage() 호출하고 flush
            Message first = persistMessage("first", "client-1", null);
            Message second = persistMessage("second", "client-2", null);
            Message third = persistMessage("third", "client-3", null);
            first.deleteMessage();
            entityManager.flush();
            // TODO: when
            long count = messageRepository.countUnread(room.getId(), null);

            // TODO: then - 삭제되지 않은 메시지 수만 반영됐는지
            assertThat(count).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("findLatestMessageIdByRoomId")
    class FindLatestMessageIdByRoomId {

        @Test
        @DisplayName("메시지가 없는 방이면 Optional.empty()를 반환한다")
        void findLatestMessageIdByRoomId_empty() {
            // TODO: when
            Optional<Long> result = messageRepository.findLatestMessageIdByRoomId(room.getId());

            // TODO: then - result가 empty인지
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("가장 최근 메시지의 id를 반환한다")
        void findLatestMessageIdByRoomId_returnsLatest() {
            // TODO: given - 메시지 여러 개 저장
            Message first = persistMessage("first", "client-1", null);
            Message second = persistMessage("second", "client-2", null);
            Message third = persistMessage("third", "client-3", null);
            // TODO: when
            Optional<Long> result = messageRepository.findLatestMessageIdByRoomId(room.getId());

            // TODO: then - result의 값이 가장 마지막에 저장한 메시지의 id와 일치하는지
            assertThat(result.get()).isEqualTo(third.getId());
        }
    }
}
