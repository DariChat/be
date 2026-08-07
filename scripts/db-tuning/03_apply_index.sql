-- room_id 단독 인덱스를 (room_id, created_at, id) 복합 인덱스로 교체
-- 실행: docker exec -i talkie-mysql-1 mysql -uroot -proot talkie < 03_apply_index.sql
--
-- FK 제약이 걸린 인덱스(FK9byh2oycnq4p3c76777tkjs6g)는 room_id 단일 컬럼이라
-- DROP 전에 FK 제약을 먼저 내려야 한다. 이후 (room_id, created_at, id) 복합 인덱스를
-- 새로 만들고 FK 제약을 그 인덱스를 타도록 다시 건다.
-- id를 3번째 컬럼으로 포함하는 이유: created_at 단독 정렬은 동일 시각 메시지의 순서를
-- 보장하지 못해 커서 페이지네이션에서 데이터 누락으로 이어진다. id를 타이브레이커로
-- 포함해 (created_at, id) 키셋 커서가 filesort 없이 이 인덱스를 그대로 타게 한다.

ALTER TABLE message DROP FOREIGN KEY FK9byh2oycnq4p3c76777tkjs6g;
ALTER TABLE message DROP INDEX FK9byh2oycnq4p3c76777tkjs6g;

CREATE INDEX idx_message_room_created ON message (room_id, created_at DESC, id DESC);

ALTER TABLE message
    ADD CONSTRAINT FK9byh2oycnq4p3c76777tkjs6g FOREIGN KEY (room_id) REFERENCES rooms (id);

SHOW INDEX FROM message;
