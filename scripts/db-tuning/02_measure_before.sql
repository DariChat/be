-- 인덱스 적용 전 측정
-- 실행: docker exec -i talkie-mysql-1 mysql -uroot -proot talkie < 02_measure_before.sql

-- 현재 message 테이블 인덱스 확인 (FK로 생성된 room_id 단일 인덱스가 있는 상태)
SHOW INDEX FROM message;

-- 대표 쿼리: 특정 방의 최신 메시지 50건 (MessageRepository.findFirstMessages와 동일한 패턴)
-- room_id 단일 인덱스만 있는 상태에서 ORDER BY created_at을 위한 별도 정렬(filesort)이 발생하는지 확인
EXPLAIN
SELECT * FROM message WHERE room_id = 42 AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 50;

EXPLAIN ANALYZE
SELECT * FROM message WHERE room_id = 42 AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 50;
