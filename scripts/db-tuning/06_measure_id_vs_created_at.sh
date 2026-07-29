#!/bin/bash
# (room_id, created_at) 복합 인덱스가 적용된 상태에서, findFirstMessages 계열 쿼리를
# id 정렬로 유지했을 때 vs created_at 정렬로 바꿨을 때를 반복 측정으로 비교한다.
# 인덱스 상태는 스크립트 밖에서 (room_id, created_at) 복합 인덱스로 미리 맞춰둔 상태여야 한다.
#
# 사용법: ./06_measure_id_vs_created_at.sh ID_SORT   또는   ./06_measure_id_vs_created_at.sh CREATED_AT_SORT
set -euo pipefail

LABEL="${1:-RUN}"
ROOM_IDS="10 25 33 47 55 66 74 91"

parse_ms() {
    local room_id=$1
    local output=$2

    local ms
    # grep이 매치를 못 찾으면(exit 1) pipefail 때문에 이 대입 자체가 set -e로 스크립트를
    # 죽여버려 아래 진단 메시지가 출력되지 못한다. `|| true`로 파이프 실패를 흡수해
    # ms가 빈 문자열인 경우를 명시적으로 검사/보고할 수 있게 한다.
    ms=$(echo "$output" | grep -oE 'Limit: 50 row\(s\).*' \
      | grep -oE 'actual time=[0-9.]+\.\.[0-9.]+' \
      | head -1 \
      | sed -E 's/actual time=[0-9.]+\.\.([0-9.]+)/\1/') || true

    if [ -z "$ms" ]; then
        echo "오류: room_id=${room_id} 측정값 파싱 실패. mysql 출력 형식이 바뀌었거나 쿼리가 실패했습니다." >&2
        echo "----- mysql 출력 -----" >&2
        echo "$output" >&2
        exit 1
    fi

    echo "$ms"
}

run_query_id_sort() {
    local room_id=$1
    local output
    output=$(docker exec -i talkie-mysql-1 mysql -uroot -proot talkie -e "
        EXPLAIN ANALYZE SELECT * FROM message WHERE room_id = ${room_id} AND deleted_at IS NULL ORDER BY id DESC LIMIT 50;
    ")
    parse_ms "$room_id" "$output"
}

run_query_created_at_sort() {
    local room_id=$1
    local output
    output=$(docker exec -i talkie-mysql-1 mysql -uroot -proot talkie -e "
        EXPLAIN ANALYZE SELECT * FROM message WHERE room_id = ${room_id} AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 50;
    ")
    parse_ms "$room_id" "$output"
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
