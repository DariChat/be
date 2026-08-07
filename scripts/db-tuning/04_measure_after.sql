-- 인덱스 적용 후 측정 (02_measure_before.sql과 동일한 쿼리로 비교)
-- 실행: docker exec -i talkie-mysql-1 mysql -uroot -proot talkie < 04_measure_after.sql

EXPLAIN
SELECT * FROM message WHERE room_id = 42 AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 50;

EXPLAIN ANALYZE
SELECT * FROM message WHERE room_id = 42 AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 50;
