# 부하 테스트

핵심 API 세 가지의 응답 시간과 동시 보태기 정합성을 측정하고, 주요 쿼리의 실행계획을 확인한다 (#207).
이어서 전체 API 기준선과 사용자 흐름 혼합 부하로 이 장비의 한계 지점과 병목을 찾는다 (#273).
로컬 수치라 운영 성능 지표가 아니라 **병목 위치와 정합성 확인**이 목적이다.

## 실행 방법

```bash
# 1. 부하 테스트 전용 DB 생성 (개발용 botem DB를 건드리지 않는다)
docker compose up -d
docker exec botem-mysql mysql -uroot -pbotem-local -e \
  "CREATE DATABASE botem_loadtest; GRANT ALL ON botem_loadtest.* TO 'botem'@'%';"

# 2. 서버 실행: Flyway가 스키마를 만든다. SQL 로그는 끄고 요청 횟수 제한(#206)은 풀어 둔다
./gradlew bootJar
SPRING_PROFILES_ACTIVE=local \
SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3306/botem_loadtest?serverTimezone=Asia/Seoul' \
SPRING_JPA_SHOW_SQL=false \
RATE_LIMIT_CONTRIBUTION_PER_MINUTE=1000000 \
RATE_LIMIT_GUEST_ACCESS_PER_MINUTE=1000000 \
RATE_LIMIT_ATTACHMENT_UPLOAD_URL_PER_MINUTE=1000000 \
java -Xmx512m -jar build/libs/botem-0.0.1-SNAPSHOT.jar

# 3. 시드 적재 후 k6가 쓸 ID 목록 추출 (fixtures.json은 커밋하지 않는다)
docker exec -i botem-mysql mysql -ubotem -pbotem-local botem_loadtest < loadtest/seed.sql
docker exec -i botem-mysql mysql -ubotem -pbotem-local botem_loadtest -N -s -r \
  < loadtest/fixtures.sql > loadtest/k6/fixtures.json

# 4. 시나리오 실행 (JWT_SECRET은 application-local.yaml의 jwt.secret)
#    쓰기가 있는 api-baseline.js·user-flow.js는 위시·확인 대기 보탬 상태를 바꾸므로, 실행할 때마다 1~3번으로 DB와 fixtures.json을 새로 만든다
cd loadtest/k6
k6 run funding-detail.js
JWT_SECRET='<jwt.secret>' k6 run home.js
k6 run contribution-concurrency.js
JWT_SECRET='<jwt.secret>' k6 run api-baseline.js          # VUS·DURATION으로 조절
JWT_SECRET='<jwt.secret>' k6 run user-flow.js             # LEVELS=1000,1500,2000 처럼 단계 지정

# 5. 실행계획
docker exec -i botem-mysql mysql -ubotem -pbotem-local botem_loadtest < loadtest/explain.sql
```

## 시드 데이터 (`seed.sql`)

| 데이터 | 규모 |
|---|---|
| 사용자 | 10,000명 (위시 프로필 키·생일 포함) |
| 진행 중 펀딩 1~5,001 | 보탬 250,000건 (#207과 동일) |
| 종료 이후 펀딩 5,002~12,001 | 선물 준비 2,500 · 종료 2,500 · 공개 대기 서프라이즈 1,000 · 공개된 서프라이즈 1,000, 펀딩당 회원 보탬 20건과 롤링페이퍼 |
| 위시 · 친구 · 추천 상품 | 사용자당 위시 10개 · 친구 30명, 추천 상품 300개 |
| 감사 카드 · 정산 계좌 | 종료 펀딩 5,000개에 1장씩, 사용자당 계좌 1개 |

## 시나리오

| 스크립트 | 대상 | 부하 | 통과 기준 |
|---|---|---|---|
| `funding-detail.js` | `GET /api/fundings/share/{shareKey}` (비로그인, 보탬 50건인 펀딩) | 50 VU · 60초 | 실패율 1% 미만, p95 300ms 미만 |
| `home.js` | `GET /api/home/fundings` (로그인) | 50 VU · 60초 | 실패율 1% 미만, p95 300ms 미만 |
| `api-baseline.js` | 조회 27개 + 쓰기 5종(위시 추가·삭제, 펀딩 개설, 회원 보태기, 입금 확인)을 하나씩 차례로 | 20 VU · API당 15초 | 실패율 1% 미만, p95 300ms 미만 |
| `user-flow.js` | 참여자 55%(상세 → 30%는 보태기) · 홈 30%(홈 → 위시 → 추천 상품 → 친구) · 개설자 15%(관리 → 확인 대기 → 입금 확인) | 50 → 600 VU 단계별 60초 | 단계별 실패율 1% 미만, p95 300ms 미만 |
| `prod-read.js` | 운영 조회 전용(공유 상세·위시 프로필·홈·추천 상품·위시) | 초당 5 → 80 요청 단계별 60초 | p95 1초 또는 실패율 5%를 넘으면 즉시 중단 |
| `contribution-concurrency.js` | `POST /api/fundings/share/{shareKey}/contributions` (같은 펀딩) | 서로 다른 비회원 200건(100 VU) + 같은 이름 50건(50 VU) | 200건 모두 성공, 같은 이름은 1건만 성공하고 49건은 409(`FUNDING_032`) |

## 결과 (2026-11-21)

환경: Apple M3 Pro(12코어), MySQL 8.4(Docker), JVM `-Xmx512m`, HikariCP 기본값(10), k6 v2.1.0. 서버와 k6를 같은 장비에서 실행했다.

| API | 처리량 | 평균 | p95 | 실패율 |
|---|---|---|---|---|
| 펀딩 상세 조회 | 1,496 req/s | 27.8ms | 62.6ms | 0% |
| 홈 펀딩 조회 | 1,859 req/s | 22.4ms | 36.5ms | 0% |
| 동시 보태기 (같은 펀딩, 100 VU) | - | 740ms | 1.55s | 의도한 409만 발생 |

### 동시 보태기 정합성

- 서로 다른 비회원 200명이 같은 펀딩에 동시에 보태면 보탬 200건과 롤링페이퍼 200건이 모두 저장되고, 금액도 유실되지 않았다.
- 같은 이름 50건이 동시에 들어오면 1건만 저장되고 49건은 `FUNDING_032`로 거절됐다.
  펀딩 행 비관적 락(`findByIdForUpdate`)이 이름 중복 확인과 저장을 직렬화하기 때문이다.

### 보태기 지연의 원인: 락이 아니라 BCrypt

| 측정 | p95 |
|---|---|
| 순차 요청 1건 | 74ms (중앙값) |
| 100 VU, **같은** 펀딩 | 1.55s |
| 100 VU, **서로 다른** 펀딩 (락 경합 없음) | 1.22~1.49s |
| 100 VU, 서로 다른 펀딩, 커넥션 풀 40 | 1.05s (최소 응답이 90ms → 248ms로 늘어남) |

- 펀딩을 나눠 락 경합을 없애도 지연이 거의 같다. 따라서 병목은 펀딩 락이 아니다.
- 비회원 비밀번호 BCrypt 해시가 병목이다. 조회 API의 최소 응답은 3ms인데 보태기는 순차 요청도 74ms이고, 이 차이의 대부분이 해시 계산(CPU 작업)이다. 커넥션 풀을 늘리면 커넥션 대기는 줄지만 CPU 경합으로 최소 응답이 늘어나, 전체 처리량은 비슷하다.
- 운영 EC2는 코어가 적어 같은 동시 요청에서 지연이 더 커진다. 다만 보태기는 IP당 분당 10회로 제한(#206)되어 있어, 한 사람이 해시 계산을 몰아서 일으킬 수는 없다.

### 쿼리 실행계획 (`explain.sql`)

| 쿼리 | 사용 인덱스 | 실제 시간 |
|---|---|---|
| 공유 키 → 펀딩 ID | `uk_fundings_share_key` (const) | 0.27ms |
| 펀딩 상세: 보탬 전체 (`ORDER BY created_at, id`) | `uk_contributions_funding_request_key` 선두 컬럼 + 정렬 50건 | 0.50ms |
| 확정 금액 합계 | `idx_contributions_funding_status` | 0.03ms |
| 같은 이름 비회원 확인 | `uk_contributions_funding_request_key` 선두 컬럼 + 필터 | 0.39ms |
| 회원 중복 참여 확인 | `idx_contributions_participant_user` | 0.002ms |
| 재시도 요청 확인 | `uk_contributions_funding_request_key` (const) | 0ms |
| 홈: 내 펀딩 목록 | `idx_fundings_owner_status_deadline` (range) | 0.04ms |
| 홈: 펀딩별 보탬 집계 | `uk_contributions_funding_request_key` 선두 컬럼 | 0.06ms |

- 전체 테이블을 스캔하는 쿼리는 없다. 보탬은 펀딩 ID로 먼저 좁혀지므로, 테이블 전체 행 수(25만 건)가 아니라 펀딩당 보탬 수에 비례한다.
- 펀딩 상세의 정렬은 인덱스 없이 메모리에서 하지만, 펀딩당 수십 건이라 0.5ms 수준이다. 그래서 `(funding_id, created_at)` 인덱스는 추가하지 않았다.
  펀딩당 보탬이 수천 건 단위로 늘면 다시 검토한다.

## 전체 API 결과 (2026-11-27, #273)

환경은 위와 같다. 요청 횟수 제한은 모두 풀었고, 스크립트마다 DB와 `fixtures.json`을 새로 만든 뒤 쟀다.
이번 측정은 **배터리 전원**에서 실행해 CPU가 제한됐을 수 있다. 전원을 연결한 첫 측정(11-26)과 비교하면 한계 지점은 같고 수치는 조금 낮다.

### API별 기준선 (`api-baseline.js`, 20 VU · 15초)

| API | req/s | 평균(ms) | p95(ms) | p99(ms) | 실패율 |
|---|---|---|---|---|---|
| **GET /api/users/me/fundings/closed** | **203** | **98.5** | **124.9** | **159.5** | 0% |
| GET /api/recommended-products | 893 | 22.3 | 35.4 | 45.4 | 0% |
| GET /api/friends/{friendId}/profile | 959 | 20.7 | 34.5 | 49.2 | 0% |
| GET /api/fundings/{fundingId}/result | 906 | 22.0 | 33.4 | 44.6 | 0% |
| POST …/contributions/{contributionId}/confirm | 1,015 | 19.7 | 27.8 | 33.9 | 0% |
| POST /api/fundings/share/{shareKey}/contributions | 1,102 | 18.1 | 25.7 | 33.5 | 0% |
| POST /api/fundings | 1,284 | 15.5 | 20.8 | 30.1 | 0% |
| POST·DELETE /api/wishes | 1,642 | 12.1 | 16.0 | 26.5 | 0% |
| 나머지 조회 23개 | 1,110~4,026 | 4.9~17.9 | 7.3~27.9 | 10.7~39.9 | 0% |

- 보관함 조회만 다른 API보다 4배 이상 느리다. 아래 [보관함 쿼리 전체 스캔](#보관함-쿼리-전체-스캔)이 원인이다.
- 쓰기는 한 번 쓴 위시·확인 대기 보탬을 다시 쓰지 않고, 무작위 회원·펀딩 조합이 겹친 중복 참여 409는 예상 응답으로 센다.

### 사용자 흐름 혼합 (`user-flow.js`)

| 동시 사용자 | req/s | 평균(ms) | p95(ms) | p99(ms) | 실패율 |
|---|---|---|---|---|---|
| 50 | 57 | 9.6 | 18.3 | 26.8 | 0% |
| 100 | 119 | 6.3 | 11.1 | 14.4 | 0% |
| 200 | 236 | 5.1 | 8.9 | 13.6 | 0% |
| 400 | 475 | 4.2 | 7.6 | 11.9 | 0% |
| 600 | 717 | 13.2 | 11.2 | 481.2 | 0% |
| 1,000 | 1,193 | 13.9 | 45.4 | 107.1 | 0% |
| **1,500** | **1,372** | **339.3** | **560.2** | **653.2** | 0% |
| 2,000 | 1,311 | 920.0 | 1,517.3 | 2,155.1 | 0% |

- 1,000명까지는 p95 50ms 이하로 여유가 있고, 1,500명부터 처리량이 초당 약 1,300~1,400건에서 멈추고 지연만 늘어난다. 실패는 끝까지 없었다.
- 첫 측정(전원 연결)도 같은 지점에서 꺾였다 (1,500명: 1,423 req/s, p95 418ms).
- 1,500명 구간 상태(첫 측정): HikariCP 커넥션 10개가 모두 사용 중이고 대기 요청이 약 189건, MySQL 컨테이너 CPU 약 420%, 서버 JVM CPU 약 230%.
  **병목은 DB CPU와 커넥션 풀 대기**다. 커넥션 풀만 늘리면 DB CPU 경합이 커지므로, DB가 처리하는 문장 수부터 줄여야 한다.
- 600명 구간 p99 481ms는 순간적인 지연으로, 원인은 확인하지 못했다. 첫 측정과 다른 단계에서는 나타나지 않았다.
- VU마다 2MB짜리 `fixtures.json`을 따로 파싱하면 k6 쪽이 먼저 느려지므로, 목록별 `SharedArray`로 한 번만 읽는다.

### Redis 캐시·요청 제한 적용 후 (#282)

같은 시드에서 동시 사용자 1,000 → 1,500 → 2,000명으로 바로 올리는 조건(앞 단계 없이 시작해 위 표보다 지연이 크다)으로 적용 전후를 비교했다.

| 동시 사용자 | 적용 전 req/s · p95 | 적용 후 req/s · p95 |
|---|---|---|
| 1,000 | 1,080 · 163ms | 1,114 · 115ms |
| 1,500 | 1,262 · 972ms | **1,439 · 443ms** |
| 2,000 | 1,225 · 1,524ms | **1,454 · 932ms** |

- 요청마다 실행되던 `SELECT status FROM users WHERE id = ?`(약 22만 회)가 캐시 적중 22만 회·미스 2.4만 회(적중률 약 90%)로 바뀌어 DB 상위 문장에서 사라졌다.
- 한계 처리량이 약 15% 늘고 1,500명 구간 p95가 절반 이하로 줄었다.

### MySQL 문장 분석 (`performance_schema`, 첫 측정의 혼합 부하 전체)

| 문장 | 실행 수 | 비고 |
|---|---|---|
| 전체 | 554만 | |
| `SET autocommit` | 125만 | 트랜잭션 시작·종료마다 |
| `SET SESSION TRANSACTION READ ONLY/WRITE` | 108만 | `readOnly` 트랜잭션 전환마다 |
| `COMMIT` | 62만 | 전체 실행 시간의 20%로 가장 큼 |
| `SELECT status FROM users WHERE id = ?` | 52만 | 인증된 요청마다 사용자 상태 확인 |

- 실행 문장의 **53%가 쿼리가 아니라 트랜잭션 제어 문장**이다. 왕복 하나하나는 짧지만 DB CPU와 커넥션 점유 시간을 늘린다.
- 개선 후보: HikariCP `auto-commit: false` + Hibernate `provider_disables_autocommit`, MySQL 드라이버 `useLocalSessionState=true`. 적용 후 같은 시나리오로 다시 측정한다.

### 보관함 쿼리 전체 스캔

`GET /api/users/me/fundings/closed`의 `findArchivedFundingsByUserId`는
`owner_id = ? OR recipient_id = ? OR EXISTS(참여한 보탬)` 조건이라 인덱스를 쓰지 못한다.

| 방식 | 읽은 행 | 실제 시간 |
|---|---|---|
| 현재: 펀딩 전체 스캔 + 펀딩마다 상관 서브쿼리(약 7천 번) | 53,647 | 53ms |
| 개선안: 참여 펀딩 ID를 먼저 조회한 뒤 `owner_id = ? OR recipient_id = ? OR id IN (…)` | 11 | 0.99ms |

- 개선안은 owner·recipient 인덱스와 PRIMARY를 합치는 index_merge로 실행된다.
- 현재 방식은 전체 펀딩 수에 비례해 느려지므로 #276에서 고친다.
- 그 밖에 전체 스캔은 없었다. 추천 상품 목록은 인덱스 없이 정렬하지만 300건이라 그대로 둔다.

### 운영 조회 측정 (`prod-read.js`)

운영 DB에 쓰지 않도록 GET만 보내고, p95 1초 또는 실패율 5%를 넘으면 스스로 멈춘다.
공유 키·위시 프로필 키·토큰은 셸 환경 변수로만 넘기고 파일이나 대화에 남기지 않는다.
사용자가 적은 시간에 Grafana(Spring Boot·Node Exporter 대시보드)를 보면서 실행한다.

```bash
cd loadtest/k6
BASE_URL='https://<운영 도메인>' SHARE_KEYS='<공유키>' WISH_PROFILE_KEYS='<위시 프로필 키>' \
ACCESS_TOKEN='<액세스 토큰>' k6 run prod-read.js
```
