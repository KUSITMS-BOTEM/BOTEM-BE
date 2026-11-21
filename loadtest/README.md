# 부하 테스트

핵심 API 세 가지의 응답 시간과 동시 보태기 정합성을 측정하고, 주요 쿼리의 실행계획을 확인한다 (#207).
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
java -Xmx512m -jar build/libs/botem-0.0.1-SNAPSHOT.jar

# 3. 시드 적재 (사용자 10,000명 · 펀딩 5,001개 · 보탬 250,000건)
docker exec -i botem-mysql mysql -ubotem -pbotem-local botem_loadtest < loadtest/seed.sql

# 4. 시나리오 실행 (JWT_SECRET은 application-local.yaml의 jwt.secret)
cd loadtest/k6
k6 run funding-detail.js
JWT_SECRET='<jwt.secret>' k6 run home.js
k6 run contribution-concurrency.js

# 5. 실행계획
docker exec -i botem-mysql mysql -ubotem -pbotem-local botem_loadtest < loadtest/explain.sql
```

## 시나리오

| 스크립트 | 대상 | 부하 | 통과 기준 |
|---|---|---|---|
| `funding-detail.js` | `GET /api/fundings/share/{shareKey}` (비로그인, 보탬 50건인 펀딩) | 50 VU · 60초 | 실패율 1% 미만, p95 300ms 미만 |
| `home.js` | `GET /api/home/fundings` (로그인) | 50 VU · 60초 | 실패율 1% 미만, p95 300ms 미만 |
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
