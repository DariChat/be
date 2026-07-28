#!/bin/bash
# BEFORE/AFTER 각각 room_id 8개 × (워밍업 1회 + 측정 5회) 반복해 평균/중앙값을 낸다.
# 사용법: ./05_measure_repeated.sh BEFORE  또는  ./05_measure_repeated.sh AFTER
# (인덱스 상태는 스크립트 밖에서 미리 맞춰둔 상태여야 한다)

LABEL="${1:-RUN}"
ROOM_IDS="10 25 33 47 55 66 74 91"

run_query() {
    local room_id=$1
    docker exec -i talkie-mysql-1 mysql -uroot -proot talkie -e "
        EXPLAIN ANALYZE SELECT * FROM message WHERE room_id = ${room_id} AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 50;
    " 2>/dev/null | grep -oE 'Limit: 50 row\(s\).*' \
      | grep -oE 'actual time=[0-9.]+\.\.[0-9.]+' \
      | head -1 \
      | sed -E 's/actual time=[0-9.]+\.\.([0-9.]+)/\1/'
}

echo "label,room_id,run,ms"
for room_id in $ROOM_IDS; do
    # 워밍업 1회 (버퍼풀에 페이지 로드, 결과는 버림)
    run_query "$room_id" > /dev/null

    for run in 1 2 3 4 5; do
        ms=$(run_query "$room_id")
        echo "${LABEL},${room_id},${run},${ms}"
    done
done
