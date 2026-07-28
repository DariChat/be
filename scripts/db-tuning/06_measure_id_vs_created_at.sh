#!/bin/bash
# (room_id, created_at) 복합 인덱스가 적용된 상태에서, findFirstMessages 계열 쿼리를
# id 정렬로 유지했을 때 vs created_at 정렬로 바꿨을 때를 반복 측정으로 비교한다.
# 인덱스 상태는 스크립트 밖에서 (room_id, created_at) 복합 인덱스로 미리 맞춰둔 상태여야 한다.
#
# 사용법: ./06_measure_id_vs_created_at.sh ID_SORT   또는   ./06_measure_id_vs_created_at.sh CREATED_AT_SORT

LABEL="${1:-RUN}"
ROOM_IDS="10 25 33 47 55 66 74 91"

run_query_id_sort() {
    local room_id=$1
    docker exec -i talkie-mysql-1 mysql -uroot -proot talkie -e "
        EXPLAIN ANALYZE SELECT * FROM message WHERE room_id = ${room_id} AND deleted_at IS NULL ORDER BY id DESC LIMIT 50;
    " 2>/dev/null | grep -oE 'Limit: 50 row\(s\).*' \
      | grep -oE 'actual time=[0-9.]+\.\.[0-9.]+' \
      | head -1 \
      | sed -E 's/actual time=[0-9.]+\.\.([0-9.]+)/\1/'
}

run_query_created_at_sort() {
    local room_id=$1
    docker exec -i talkie-mysql-1 mysql -uroot -proot talkie -e "
        EXPLAIN ANALYZE SELECT * FROM message WHERE room_id = ${room_id} AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 50;
    " 2>/dev/null | grep -oE 'Limit: 50 row\(s\).*' \
      | grep -oE 'actual time=[0-9.]+\.\.[0-9.]+' \
      | head -1 \
      | sed -E 's/actual time=[0-9.]+\.\.([0-9.]+)/\1/'
}

if [ "$LABEL" = "CREATED_AT_SORT" ]; then
    run_query() { run_query_created_at_sort "$1"; }
else
    run_query() { run_query_id_sort "$1"; }
fi

echo "label,room_id,run,ms"
for room_id in $ROOM_IDS; do
    run_query "$room_id" > /dev/null   # 워밍업

    for run in 1 2 3 4 5; do
        ms=$(run_query "$room_id")
        echo "${LABEL},${room_id},${run},${ms}"
    done
done
