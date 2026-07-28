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
            DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 60 * 24 * 60) MINUTE),
            base_room_id + FLOOR(RAND() * 100),
            base_user_id + FLOOR(RAND() * 50)
        FROM (
            SELECT (a.N + b.N * 10 + c.N * 100 + d.N * 1000) AS n
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
