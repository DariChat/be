-- k6 WebSocket 부하 테스트용 시딩: user 50명, room 10개(방마다 5명)
-- 기존 scripts/db-tuning/01_seed.sql과 구분하기 위해 이메일 접두사를 k6user로 둔다.
-- 비밀번호는 전부 'password123' (아래 BCrypt 해시와 매칭됨, db-tuning/01_seed.sql과 동일 해시 재사용)
-- 재실행 가능: 실행 전 k6user*/k6-room-* 관련 레코드를 의존성 역순(message → room_members → rooms/users)으로
-- 먼저 정리하므로, 같은 DB에 여러 번 실행해도 매번 깨끗한 상태에서 새로 시딩된다.
-- 실행: docker exec -i talkie-mysql-1 mysql -uroot -proot talkie < seed.sql

SET @start_time = NOW();

-- ── 이전 실행분 정리 (의존성 역순) ──────────────────────────────────────────
-- room_members는 room_name(k6-room-%) 기준뿐 아니라 user_id(k6user%) 기준으로도
-- 정리해야 한다 — db-tuning/01_seed.sql 같은 다른 시딩 스크립트가 user_id를
-- 산술로 계산해 room_member를 만들 경우, 그 시점에 살아있던 k6user가 우연히
-- seed-room-* 같은 다른 방의 멤버로도 잡혀 users 삭제 시 FK 제약을 위반할 수
-- 있다(실측 확인).
DELETE m FROM message m
    JOIN rooms r ON r.id = m.room_id
    WHERE r.room_name LIKE 'k6-room-%';
DELETE m FROM message m
    JOIN users u ON u.id = m.user_id
    WHERE u.email LIKE 'k6user%@seed.com';
DELETE rm FROM room_members rm
    JOIN rooms r ON r.id = rm.room_id
    WHERE r.room_name LIKE 'k6-room-%';
DELETE rm FROM room_members rm
    JOIN users u ON u.id = rm.user_id
    WHERE u.email LIKE 'k6user%@seed.com';
DELETE FROM rooms WHERE room_name LIKE 'k6-room-%';
DELETE FROM users WHERE email LIKE 'k6user%@seed.com';

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
-- room r(1~10)에는 k6user{(r-1)*5+1} ~ k6user{(r-1)*5+5}가 배정된다. users.id가
-- 연속 할당된다는 가정(base_user_id + offset 산술) 대신, 매번 이메일로 직접
-- 조회해서 배정한다 — 시딩 도중 다른 유저 INSERT가 끼어들어도 영향받지 않는다.
DROP PROCEDURE IF EXISTS seed_k6_room_members;
DELIMITER $$
CREATE PROCEDURE seed_k6_room_members()
BEGIN
    DECLARE r INT DEFAULT 1;

    WHILE r <= 10 DO
        INSERT INTO room_members (role, user_id, room_id, joined_at)
        SELECT 'MEMBER', u.id, k6_room.id, NOW()
        FROM (SELECT 0 AS n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4) AS members
        JOIN users u
          ON u.email = CONCAT('k6user', ((r - 1) * 5 + members.n + 1), '@seed.com')
        JOIN rooms k6_room
          ON k6_room.room_name = CONCAT('k6-room-', r);
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
