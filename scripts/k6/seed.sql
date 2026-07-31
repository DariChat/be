-- k6 WebSocket 부하 테스트용 시딩: user 50명, room 10개(방마다 5명)
-- 기존 scripts/db-tuning/01_seed.sql과 구분하기 위해 이메일 접두사를 k6user로 둔다.
-- 비밀번호는 전부 'password123' (아래 BCrypt 해시와 매칭됨, db-tuning/01_seed.sql과 동일 해시 재사용)
-- 실행: docker exec -i talkie-mysql-1 mysql -uroot -proot talkie < seed.sql

SET @start_time = NOW();

-- ── users: k6user1 ~ k6user50 ──────────────────────────────────────────────
DROP PROCEDURE IF EXISTS seed_k6_users;
DELIMITER $$
CREATE PROCEDURE seed_k6_users()
BEGIN
    DECLARE i INT DEFAULT 1;
    WHILE i <= 50 DO
        INSERT INTO users (name, password, email, nickname, profile_image_url, last_active_at, created_at)
        VALUES (
            CONCAT('k6user', i),
            '$2a$10$QZo8WRjIvfcN10YU1o.SF.pFERsHHqNq6B/5LmJNcnBA7iLakLwG.',
            CONCAT('k6user', i, '@seed.com'),
            CONCAT('k6_user', i),
            NULL,
            NOW(),
            NOW()
        );
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL seed_k6_users();
DROP PROCEDURE seed_k6_users;

-- ── rooms: k6-room-1 ~ k6-room-10 ──────────────────────────────────────────
DROP PROCEDURE IF EXISTS seed_k6_rooms;
DELIMITER $$
CREATE PROCEDURE seed_k6_rooms()
BEGIN
    DECLARE i INT DEFAULT 1;
    WHILE i <= 10 DO
        INSERT INTO rooms (room_name, room_type, created_at)
        VALUES (CONCAT('k6-room-', i), 'GROUP', NOW());
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL seed_k6_rooms();
DROP PROCEDURE seed_k6_rooms;

-- ── room_members: 유저 50명을 방 10개에 5명씩 겹치지 않게 전부 배정 ──
-- room r(1~10)에 유저 오프셋 (r-1)*5 + n (n=0..4)을 배정한다. 유저 수(50)와
-- 방*정원(10*5=50)이 정확히 일치하므로, 이 방식이면 유저 50명 전원이 정확히
-- 방 하나씩만 갖게 되어 k6 스크립트가 "속한 방이 없는 유저"를 만나지 않는다.
-- ((r + n) % 50처럼 유저 수보다 훨씬 큰 범위의 room_id를 전제한 배정 공식은
-- 방 개수가 적을 때 도달 불가능한 오프셋이 생겨 유저 다수가 미배정되는 문제가 있었다.
DROP PROCEDURE IF EXISTS seed_k6_room_members;
DELIMITER $$
CREATE PROCEDURE seed_k6_room_members()
BEGIN
    DECLARE r INT DEFAULT 1;
    DECLARE base_user_id BIGINT;
    SELECT MIN(id) INTO base_user_id FROM users WHERE email LIKE 'k6user%@seed.com';

    WHILE r <= 10 DO
        INSERT INTO room_members (role, user_id, room_id, joined_at)
        SELECT 'MEMBER', base_user_id + ((r - 1) * 5 + n), (SELECT id FROM rooms WHERE room_name = CONCAT('k6-room-', r)), NOW()
        FROM (SELECT 0 AS n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4) AS members;
        SET r = r + 1;
    END WHILE;
END$$
DELIMITER ;
CALL seed_k6_room_members();
DROP PROCEDURE seed_k6_room_members;

SELECT
    (SELECT COUNT(*) FROM users WHERE email LIKE 'k6user%@seed.com') AS k6_user_count,
    (SELECT COUNT(*) FROM rooms WHERE room_name LIKE 'k6-room-%') AS k6_room_count,
    (SELECT COUNT(*) FROM room_members rm JOIN rooms r ON r.id = rm.room_id WHERE r.room_name LIKE 'k6-room-%') AS k6_room_member_count,
    TIMEDIFF(NOW(), @start_time) AS elapsed;
