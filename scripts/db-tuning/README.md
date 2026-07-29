# DB 인덱스 튜닝 — message 테이블

이슈: 메시지 테이블에 대량 데이터를 넣고 인덱스 적용 전후 성능을 비교한다.

대상 쿼리 (방 입장 시 최신 메시지 조회, `MessageRepository.findFirstMessages`와 동일한 패턴):

```sql
SELECT * FROM message WHERE room_id = ? AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 50;
```

## 1. 더미 데이터

- `01_seed.sql` — user 50명, room 100개, room_member 500건(방마다 5명), **message 100만 건**을 생성
- message는 `room_id`를 100개에 고르게 랜덤 분산시키고, `created_at`도 최근 60일 사이로 흩뿌려 실제 서비스와 비슷한 분포를 흉내냄 (방 1개에 몰아넣으면 `room_id` 필터링 자체가 의미 없어짐)
- 100만 건 삽입에 약 13초 소요 (배치 INSERT 200회 × 5,000행, 저장 프로시저 + 숫자 시퀀스 카티전곱으로 row 생성)

실행:
```bash
docker exec -i talkie-mysql-1 mysql -uroot -proot talkie < 01_seed.sql
```

## 2. 인덱스 적용 전 측정

인덱스 적용 전 상태는 JPA가 `room_id` FK에 자동으로 만들어주는 단일 컬럼 인덱스만 있는 상태다.

```
possible_keys: FK9byh2oycnq4p3c76777tkjs6g (room_id)
key:           FK9byh2oycnq4p3c76777tkjs6g
rows:          ~10,000 (해당 방의 전체 메시지 수만큼)
Extra:         Using where; Using filesort
```

`room_id`로 인덱스를 태워 해당 방의 메시지(방마다 약 1만 건)를 전부 찾아온 뒤, `created_at` 정렬 기준이 인덱스에 없어 **filesort(메모리/디스크 정렬)** 가 별도로 발생한다. 이후 상위 50건만 잘라낸다 — 즉 "50건만 필요한데 1만 건을 다 읽고 정렬"하는 낭비 구조.

## 3. `(room_id, created_at DESC)` 복합 인덱스 적용

```sql
CREATE INDEX idx_message_room_created ON message (room_id, created_at DESC);
```

(`room_id` 단일 인덱스를 대체 — 자세한 절차는 `03_apply_index.sql`, FK 제약 때문에 인덱스 교체 전에 FK를 내렸다가 다시 걸어야 함)

## 4. 인덱스 적용 후 측정

```
possible_keys: idx_message_room_created
key:           idx_message_room_created
rows:          50
Extra:         Using where   (filesort 사라짐)
```

`room_id`로 좁힌 뒤 그 안에서 이미 `created_at DESC` 순으로 정렬된 인덱스를 그대로 순서대로 읽으면 되므로, 필요한 50건만 읽고 바로 멈춘다(`rows=50 loops=1`).

## 5. 전후 비교 (반복 측정, 캐시 워밍업 통제)

단발성 측정은 InnoDB 버퍼풀 캐싱 여부에 따라 같은 쿼리도 몇 배씩 편차가 났다 (콜드 상태에서 100ms대, 워밍업 후 1ms대까지 요동). 이를 통제하기 위해 `05_measure_repeated.sh`로 **room 8개 × (워밍업 1회 + 측정 5회, 총 40회 샘플)** 를 BEFORE/AFTER 각각 수행했다.

```bash
./05_measure_repeated.sh BEFORE > before_results.csv   # 인덱스 상태를 맞춘 뒤 실행
./05_measure_repeated.sh AFTER  > after_results.csv
```

**요약 (n=40, 워밍업 후 측정치만 집계):**

| | BEFORE (room_id 단일 인덱스) | AFTER (복합 인덱스) |
|---|---|---|
| mean | 33.37 ms | 0.283 ms |
| median | 30.80 ms | 0.195 ms |
| min | 27.60 ms | 0.131 ms |
| max | 59.70 ms | 1.420 ms |

- **평균 기준 117.9배, 중앙값 기준 158.4배 개선**
- BEFORE는 room마다 스캔해야 하는 행 수(~1만 건)가 비슷해 분산이 크지 않고(27.6~59.7ms), 가끔 발생하는 상위 이상치(52~60ms대)는 버퍼풀 페이지 교체 등 시스템 잡음으로 추정
- AFTER는 애초에 50건만 읽으므로 값 자체가 1ms 미만대로 작아, 상대적으로 편차(0.13~1.42ms)가 더 도드라져 보이지만 절대 크기는 무시할 수준

**room별 중앙값 비교:**

| room_id | BEFORE median | AFTER median | 개선 배수 |
|---|---|---|---|
| 10 | 28.20 ms | 0.362 ms | 77.9x |
| 25 | 31.10 ms | 0.278 ms | 111.9x |
| 33 | 33.40 ms | 0.204 ms | 163.7x |
| 47 | 29.80 ms | 0.176 ms | 169.3x |
| 55 | 30.50 ms | 0.227 ms | 134.4x |
| 66 | 34.20 ms | 0.177 ms | 193.2x |
| 74 | 30.50 ms | 0.185 ms | 164.9x |
| 91 | 31.30 ms | 0.151 ms | 207.3x |

읽은 행 수는 room당 ~10,000행 → 50행으로 줄었다 (`Using filesort` 제거). 이 스캔 범위 축소가 실행시간 감소의 근본 원인이며, 배수 자체(약 80~210배, 조건에 따라 변동)보다는 **"filesort가 사라지고 스캔 범위가 200배 줄었다"는 메커니즘이 재현성 있는 결론**이다.

## 6. B-tree 인덱스 원리와 이 케이스에 맞는 이유

- MySQL(InnoDB)의 인덱스는 기본적으로 **B+Tree** 구조 — 키로 정렬된 노드를 따라가며 탐색하므로 정렬된 순서로 값을 순차적으로 읽는 데 유리하다.
- **복합 인덱스는 왼쪽 컬럼부터 순서대로 정렬**된다. `(room_id, created_at DESC)`는 먼저 `room_id`로 그룹핑되고, 같은 `room_id` 안에서는 `created_at DESC` 순으로 이미 정렬돼 있다.
- 그래서 `WHERE room_id = ? ORDER BY created_at DESC`는 인덱스의 해당 `room_id` 구간을 찾아 그 순서 그대로 앞에서부터 읽기만 하면 된다 — **별도 정렬(filesort) 없이 인덱스 자체가 정렬을 제공**한다. 이게 인덱스 적용 후 `Extra`에서 `Using filesort`가 사라진 이유.
- 반대로 `room_id` 단일 인덱스는 "room_id로 좁히는 것"까지만 인덱스가 해주고, 그 안에서의 정렬은 인덱스가 보장하지 않으므로 MySQL이 결과를 메모리/디스크에 모아 별도로 정렬해야 한다 — 그게 filesort.

### 카디널리티 낮은 컬럼 단독 인덱스를 지양하는 이유

- 인덱스는 "이 조건으로 얼마나 후보를 좁힐 수 있는가"(선택도, selectivity)가 성능의 핵심이다. **카디널리티(distinct 값의 개수)가 낮으면 선택도가 낮아, 인덱스를 걸어도 후보가 거의 안 좁혀진다.**
- 이번 데이터로 실측: `publish_status` 컬럼은 100만 건 중 distinct 값이 1개(전부 PUBLISHED)뿐이다. 이 컬럼에 단독 인덱스를 걸고 `EXPLAIN`을 보면:
  ```
  key: idx_publish_status_demo
  rows: 496380   -- 전체 100만 건 중 절반가량을 "후보"로 추정
  ```
  인덱스를 "타긴" 하지만 사실상 테이블의 거의 절반을 가리키므로, 인덱스를 통한 랜덤 I/O(인덱스 리프 → 실제 row로 점프하는 비용)가 그냥 테이블을 순차로 풀스캔하는 것보다 오히려 느릴 수 있다. 게다가 인덱스는 쓰기(INSERT/UPDATE) 시마다 유지 비용이 드므로, 효과 없는 인덱스는 순수 비용만 남긴다.
- `deleted_at`도 마찬가지로 이번 데이터에서 distinct 값이 0(전부 NULL)이라 단독 인덱스를 걸 이유가 없다. 이런 저카디널리티 컬럼은 **복합 인덱스의 뒤쪽 필터 조건**으로 붙이거나(이번처럼 `WHERE ... AND deleted_at IS NULL`을 `Filter`로 처리하게 두는 것), 아예 인덱스 대상에서 제외하는 편이 낫다.
- 반면 `room_id`는 distinct 값이 101개로 전체 100만 건을 101개 그룹으로 잘게 쪼개주므로 선택도가 훨씬 높고, 여기에 정렬 컬럼(`created_at`)을 덧붙이는 복합 인덱스가 이 쿼리 패턴에 가장 잘 맞는다.

## 7. 이 인덱스 교체가 다른 쿼리에 미치는 영향 (트레이드오프)

`MessageRepository`에서 `room_id`를 조건으로 쓰는 쿼리는 이번에 튜닝한 것 말고도 3개 더 있다:

```java
findMessages(roomId, cursor, size)           // ORDER BY id DESC — 커서 페이지네이션
findFirstMessages(roomId, size)               // ORDER BY id DESC — 첫 페이지
findLatestMessageIdByRoomId(roomId)           // ORDER BY id DESC LIMIT 1
```

이 셋은 정렬 기준이 `created_at`이 아니라 **`id`(PK)** 다. `findMessages`/`findFirstMessages`는 `MessageService.findMessagesByRoomId`를 거쳐 `MessageController`(메시지 히스토리 조회 API)가 실제로 호출하는, 사용 빈도가 높은 경로다.

인덱스 교체 전후로 이 쿼리들의 계획이 어떻게 바뀌는지 직접 확인했다:

| 쿼리 (`ORDER BY id DESC LIMIT 50`, room_id=42) | BEFORE (room_id 단일 인덱스) | AFTER (room_id, created_at 복합 인덱스) |
|---|---|---|
| 실행 계획 | `room_id` 인덱스로 좁힌 뒤 그 안에서 id 역순 스캔 | PK(`id`) 전체를 역순으로 스캔하며 `room_id` 필터링 |
| 실측 시간 | 4.54 ms | 31.6 ms (**약 7배 느려짐**) |

**원인:** InnoDB의 세컨더리 인덱스는 각 엔트리에 PK를 함께 저장하기 때문에, `room_id` 단일 인덱스로 좁혀진 상태에서는 그 안에서 `id` 역순 스캔도 자연스럽게 잘 맞는다(id가 auto-increment라 사실상 삽입 순서 = created_at 순서와 거의 일치). 그런데 인덱스를 `(room_id, created_at)`로 바꾸면 `id` 정렬은 더 이상 인덱스가 보장해주지 않으므로, 옵티마이저는 `room_id`로 필터링하며 정렬을 다시 하느니 **차라리 PK를 통째로 역순 스캔하는 쪽을 선택**한다 (`FORCE INDEX`로 새 인덱스를 강제해봐도 filesort가 발생해 261ms로 더 느려짐 — PRIMARY 스캔이 그나마 나은 차선책이었던 것).

`findLatestMessageIdByRoomId`(LIMIT 1)는 0.56ms 수준이라 영향이 미미하지만, `findMessages`/`findFirstMessages`(LIMIT 50, 대량 페이지네이션에 반복 호출됨)는 실제 회귀다.

**결론 — 이건 "이 인덱스가 틀렸다"가 아니라 트레이드오프다:**
- 이번 이슈의 대상 쿼리(`created_at` 정렬)만 보면 압도적으로 개선되지만, 같은 테이블의 다른 쿼리(`id` 정렬)는 손해를 본다 — **인덱스는 공짜가 아니고, 한 쿼리 패턴에 맞추면 다른 패턴이 희생될 수 있다**는 걸 실측으로 확인한 것.
- `id`는 DB가 편의상 자동 증가시키는 대리키일 뿐, "메시지가 온 시간 순서"라는 의미를 담고 있는 건 `created_at`이다. 애초에 `id` 정렬은 "auto-increment라 시간 순서랑 거의 같으니 대충 써도 되겠지"라는 암묵적 가정이었을 뿐, 의미상으로도 `created_at` 정렬이 맞다 — 그래서 **정렬 기준 자체를 `created_at`으로 통일**하는 쪽으로 코드를 변경했다 (아래 8절).

### 8. 후속 조치 — findMessages/findFirstMessages 정렬 기준을 created_at으로 통일, (created_at, id) 키셋 커서로 정정

`MessageRepository`/`MessageService`/`MessageController`의 커서 페이지네이션을 `id` 기준에서 `created_at` 기준으로 바꿨다.

**시행착오:** 처음엔 `created_at`만으로는 동시간대 메시지에서 커서가 흔들릴 수 있다고 보고 `(createdAt, id)` 튜플을 커서로 쓰려 했다:
```sql
WHERE created_at < :cursor OR (created_at = :cursor AND id < :cursorId)
ORDER BY created_at DESC, id DESC
```
그런데 당시 인덱스가 `(room_id, created_at DESC)` 2컬럼뿐이라, 이 `OR` 조건과 `id` 보조 정렬을 EXPLAIN으로 확인하니 **filesort가 발생**했다 (`Sort: message.created_at DESC, message.id DESC`, 2204ms). MySQL 8.0의 로우 값 비교(`(created_at, id) < (?, ?)`)로도 시도했지만 인덱스 access 조건이 아니라 `Filter`로만 처리되어 마찬가지로 filesort가 붙었다 (648ms).

**잘못된 결론(정정됨):** 이 시점에는 "id 타이브레이커를 추가하면 filesort가 재발한다"고 판단해, `created_at` 단독으로 정렬/커서 조건을 단순화하고 "`LocalDateTime`이 마이크로초 정밀도라 실사용에서 동시간대 커서 충돌 가능성은 무시할 수준"이라는 근거로 정확성을 포기했었다. **이 근거는 틀렸다** — Hibernate 기본 매핑에서 `LocalDateTime` → MySQL `datetime` 컬럼은 초 단위로 저장되며(실제로 `SHOW INDEX`의 `created_at` cardinality가 전체 행 수보다 작게 나옴), 실시간 채팅에서는 같은 초에 여러 메시지가 쌓이는 일이 드물지 않다. `created_at < cursor` 단독 조건은 커서와 정확히 같은 시각의 메시지를 다음 페이지에서 **영구히 누락**시킨다 — 성능을 이유로 데이터 유실을 허용한 셈이었다.

**정정된 원인 분석과 해결:** filesort가 발생했던 진짜 이유는 "타이브레이커를 추가해서"가 아니라, **인덱스 자체에 `id`가 없어서** 옵티마이저가 `id` 정렬을 인덱스로 보장받지 못했기 때문이다. `idx_message_room_created`를 `(room_id, created_at DESC, id DESC)` 3컬럼으로 재구성하니, 동일한 OR 조건 쿼리가 filesort 없이 인덱스 하나로 해결된다.

```sql
WHERE room_id = ? AND (created_at < :cursorCreatedAt OR (created_at = :cursorCreatedAt AND id < :cursorId))
ORDER BY created_at DESC, id DESC LIMIT :size
```

**결과 (EXPLAIN ANALYZE, room_id=42, 3컬럼 인덱스 적용 후):**

| 쿼리 | 정렬 기준 | Extra | 실행 계획 |
|---|---|---|---|
| findFirstMessages | created_at DESC, id DESC | `Using where` (filesort 없음) | `Index lookup on message using idx_message_room_created` |
| findMessages (키셋 커서) | created_at DESC, id DESC, OR 조건 | `Using index condition; Using where` (filesort 없음) | `Index range scan on message using idx_message_room_created` |

키셋 커서 쿼리를 반복 측정한 결과(room 8개 × 5회, 워밍업 포함, 실제 마지막 메시지의 (created_at, id)를 커서로 사용): mean 0.532ms, median 0.236ms (n=40) — 기존 `created_at` 단독 커서(mean 0.283ms, median 0.195ms)보다 약간 느리지만 여전히 서브밀리초대이고, `id` 단독 정렬(median 1.31ms)보다는 훨씬 빠르다. OR 분기 평가 비용만큼 소폭 느려진 것으로, filesort가 다시 발생하는 것과는 근본적으로 다른 수준의 차이다. **정확성(동시각 메시지 누락 방지)을 포기하지 않고도 인덱스를 온전히 활용할 수 있었다** — API 파라미터도 `cursor: Long`(마지막 메시지 id) → `cursor: LocalDateTime`(created_at 단독) 대신, `cursorCreatedAt`(LocalDateTime) + `cursorId`(Long) 두 값으로 구성된 키셋 커서로 노출한다.

## 파일 구성

- `01_seed.sql` — 더미 데이터 생성 (user 50 / room 100 / message 100만)
- `02_measure_before.sql` — 인덱스 적용 전 `EXPLAIN` / `EXPLAIN ANALYZE` (단발성, 계획 확인용)
- `03_apply_index.sql` — `room_id` 단일 인덱스를 `(room_id, created_at DESC, id DESC)` 복합 인덱스로 교체 (id는 (created_at, id) 키셋 커서를 filesort 없이 지원하기 위한 타이브레이커)
- `04_measure_after.sql` — 인덱스 적용 후 `EXPLAIN` / `EXPLAIN ANALYZE` (단발성, 계획 확인용)
- `05_measure_repeated.sh` — room 8개 × 5회 반복 측정(워밍업 포함)으로 캐시 편차를 통제한 정량 비교용 스크립트 (5절 수치의 근거)
- `06_measure_id_vs_created_at.sh` — 복합 인덱스가 적용된 상태에서 id 정렬 vs created_at 정렬을 반복 측정으로 비교 (8절 수치의 근거)

애플리케이션 엔티티(`Message.java`)에도 `@Table(indexes = ...)`로 동일한 인덱스를 정의해뒀다. 다만 실제 배포 설정(`application-deploy.yml`)의 `ddl-auto`는 `update`이고, 이 값은 기존 스키마를 변경하지 않으므로 엔티티에 인덱스를 정의해두는 것만으로 배포 환경에 인덱스가 자동 반영되지는 않는다. 이 벤치마크가 실제로 반영/재현되는 경로는 `03_apply_index.sql`을 수동으로 실행하는 것이며, 이 문서의 BEFORE/AFTER 측정도 그 경로로 인덱스를 넣고 뺀 상태를 기준으로 했다. (`application-local.yml`은 로컬 전용으로 `ddl-auto: create`를 쓰지만, 이는 매 기동 시 스키마를 초기화하는 로컬 개발 편의 설정일 뿐 이 벤치마크의 재현 경로와는 무관하다.)
