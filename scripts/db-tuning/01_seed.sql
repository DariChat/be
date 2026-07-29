-- 더미 데이터 시딩: user 50명, room 100개, message 100만 건
-- room당 메시지 수를 고르게 분산시켜 room_id로 필터링하는 쿼리의 인덱스 효과를 관찰할 수 있게 한다.
-- 실행: docker exec -i talkie-mysql-1 mysql -uroot -proot talkie < 01_seed.sql

SET @start_time = NOW();

-- ── users ──────────────────────────────────────────────
DROP PROCEDURE IF EXISTS seed_users;
DELIMITER $$
CREATE PROCEDURE seed_users()
BEGIN
    DECLARE i INT DEFAULT 1;
    WHILE i <= 50 DO
        INSERT INTO users (name, password, email, nickname, profile_image_url, last_active_at, created_at)
        VALUES (
            CONCAT('user', i),
            '$2a$10$QZo8WRjIvfcN10YU1o.SF.pFERsHHqNq6B/5LmJNcnBA7iLakLwG.',
            CONCAT('user', i, '@seed.com'),
            CONCAT('seed_user', i),
            NULL,
            NOW(),
            NOW()
        );
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL seed_users();
DROP PROCEDURE seed_users;

-- ── rooms ──────────────────────────────────────────────
DROP PROCEDURE IF EXISTS seed_rooms;
DELIMITER $$
CREATE PROCEDURE seed_rooms()
BEGIN
    DECLARE i INT DEFAULT 1;
    WHILE i <= 100 DO
        INSERT INTO rooms (room_name, room_type, created_at)
        VALUES (CONCAT('seed-room-', i), 'GROUP', NOW());
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL seed_rooms();
DROP PROCEDURE seed_rooms;

-- ── room_members: room마다 5명씩 배정 ─────────────────────
DROP PROCEDURE IF EXISTS seed_room_members;
DELIMITER $$
CREATE PROCEDURE seed_room_members()
BEGIN
    DECLARE r INT DEFAULT 1;
    DECLARE base_user_id BIGINT;
    SELECT MIN(id) INTO base_user_id FROM users WHERE email LIKE '%@seed.com';

    WHILE r <= 100 DO
        INSERT INTO room_members (role, user_id, room_id, joined_at)
        SELECT 'MEMBER', base_user_id + ((r + n) % 50), (SELECT id FROM rooms WHERE room_name = CONCAT('seed-room-', r)), NOW()
        FROM (SELECT 0 AS n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4) AS members;
        SET r = r + 1;
    END WHILE;
END$$
DELIMITER ;
CALL seed_room_members();
DROP PROCEDURE seed_room_members;

-- ── message: 100만 건, room_id 1~100(오프셋 기준)에 분산, created_at은 과거로 흩뿌림 ──
-- 작성자는 반드시 해당 방의 room_members 5명 중 한 명이어야 한다(MessageService.saveMessage가
-- 강제하는 멤버십 규칙과 일치하는 데이터를 만들기 위함). room_members는 room r에
-- base_user_id + ((r + n) % 50), n=0..4 규칙으로 배정되어 있으므로, 방을 먼저 고른 뒤
-- 그 방의 멤버 5명 중 하나를 같은 규칙으로 골라 작성자로 삼는다.
DROP PROCEDURE IF EXISTS seed_messages;
DELIMITER $$
CREATE PROCEDURE seed_messages()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE batch_size INT DEFAULT 5000;
    DECLARE total_batches INT DEFAULT 200; -- 5000 * 200 = 1,000,000
    DECLARE base_room_id BIGINT;
    DECLARE base_user_id BIGINT;
    SELECT MIN(id) INTO base_room_id FROM rooms WHERE room_name LIKE 'seed-room-%';
    SELECT MIN(id) INTO base_user_id FROM users WHERE email LIKE '%@seed.com';

    SET autocommit = 0;
    WHILE i < total_batches DO
        INSERT INTO message (content, client_message_id, publish_status, created_at, room_id, user_id)
        SELECT
            CONCAT('seed message #', i * batch_size + seq.n),
            UUID(),
            'PUBLISHED',
            -- 최근 60일 사이로 흩뿌려서 시간 범위 쿼리도 자연스럽게 함
            DATE_SUB(NOW(), INTERVAL FLOOR(RAND(i * batch_size + seq.n) * 60 * 24 * 60) MINUTE),
            base_room_id + seq.picked_room_offset,
            -- room_members 배정 규칙(base_user_id + ((r + n) % 50))과 동일하게,
            -- 고른 방(room 번호 = picked_room_offset + 1)의 멤버 5명 중 하나를 선택.
            -- RAND()는 같은 행 안에서 여러 번 참조되면 매번 재평가되어 값이 달라지므로
            -- (검증됨: 파생 테이블로 감싸도 재평가됨), 전역 행 번호를 시드로 준
            -- RAND(seed)로 결정적으로 고정한다.
            base_user_id + ((seq.picked_room_offset + 1 + FLOOR(RAND(i * batch_size + seq.n + 1) * 5)) % 50)
        FROM (
            SELECT (a.N + b.N * 10 + c.N * 100 + d.N * 1000) AS n,
                   FLOOR(RAND(i * batch_size + (a.N + b.N * 10 + c.N * 100 + d.N * 1000)) * 100) AS picked_room_offset
            FROM
                (SELECT 0 N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
                 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a,
                (SELECT 0 N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
                 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b,
                (SELECT 0 N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
                 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) c,
                (SELECT 0 N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4) d
        ) AS seq
        WHERE seq.n < batch_size;

        COMMIT;
        SET i = i + 1;
    END WHILE;
    SET autocommit = 1;
END$$
DELIMITER ;
CALL seed_messages();
DROP PROCEDURE seed_messages;

SELECT
    (SELECT COUNT(*) FROM users) AS user_count,
    (SELECT COUNT(*) FROM rooms) AS room_count,
    (SELECT COUNT(*) FROM room_members) AS room_member_count,
    (SELECT COUNT(*) FROM message) AS message_count,
    TIMEDIFF(NOW(), @start_time) AS elapsed;
