-- room_id 단독 인덱스를 (room_id, created_at) 복합 인덱스로 교체
-- 실행: docker exec -i talkie-mysql-1 mysql -uroot -proot talkie < 03_apply_index.sql
--
-- FK 제약이 걸린 인덱스(FK9byh2oycnq4p3c76777tkjs6g)는 room_id 단일 컬럼이라
-- DROP 전에 FK 제약을 먼저 내려야 한다. 이후 (room_id, created_at) 복합 인덱스를
-- 새로 만들고 FK 제약을 그 인덱스를 타도록 다시 건다.

ALTER TABLE message DROP FOREIGN KEY FK9byh2oycnq4p3c76777tkjs6g;
ALTER TABLE message DROP INDEX FK9byh2oycnq4p3c76777tkjs6g;

CREATE INDEX idx_message_room_created ON message (room_id, created_at DESC);

ALTER TABLE message
    ADD CONSTRAINT FK9byh2oycnq4p3c76777tkjs6g FOREIGN KEY (room_id) REFERENCES rooms (id);

SHOW INDEX FROM message;
