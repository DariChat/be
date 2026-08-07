-- 로컬 개발용 테스트 데이터 (ddl-auto: create 라 서버 뜰 때마다 재생성됨)
-- 모든 유저 비밀번호: password123

INSERT INTO users (name, password, email, nickname, profile_image_url, last_active_at, created_at) VALUES
    ('tester1', '$2a$10$QZo8WRjIvfcN10YU1o.SF.pFERsHHqNq6B/5LmJNcnBA7iLakLwG.', 'test1@test.com', 'tester1', NULL, NOW(), NOW()),
    ('tester2', '$2a$10$QZo8WRjIvfcN10YU1o.SF.pFERsHHqNq6B/5LmJNcnBA7iLakLwG.', 'test2@test.com', 'tester2', NULL, NOW(), NOW());

INSERT INTO rooms (room_name, room_type, created_at) VALUES
    ('테스트방', 'GROUP', NOW());

INSERT INTO room_members (role, user_id, room_id, joined_at) VALUES
    ('OWNER', 1, 1, NOW()),
    ('MEMBER', 2, 1, NOW());