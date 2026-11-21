-- 부하 테스트 대상 API가 실행하는 주요 쿼리의 실행계획. seed.sql을 넣은 botem_loadtest에서 실행한다.
-- 펀딩 1번(보탬 50건), 개설자 1번, 회원 참여자 5002번 기준

-- [공유 화면 진입] 공유 키로 펀딩 ID 조회 (FundingShareKeyResolver)
EXPLAIN ANALYZE
SELECT id FROM fundings WHERE share_key = LPAD(HEX(1), 32, '0');

-- [펀딩 상세] 펀딩의 보탬 전체를 참여 순서대로 조회 (FundingDetailService)
EXPLAIN ANALYZE
SELECT * FROM contributions WHERE funding_id = 1 ORDER BY created_at, id;

-- [보태기] 확정 금액 합계 (ContributionService.validateContributable, 펀딩 락 안)
EXPLAIN ANALYZE
SELECT COALESCE(SUM(amount), 0) FROM contributions WHERE funding_id = 1 AND status = 'CONFIRMED';

-- [보태기] 같은 이름의 비회원 보탬 여부 (validateGuestIdentityAvailable, 펀딩 락 안)
EXPLAIN ANALYZE
SELECT * FROM contributions
WHERE funding_id = 1 AND participant_user_id IS NULL AND participant_name = '참여자1' AND status <> 'CANCELLED';

-- [보태기] 회원 중복 참여 여부 (validateMemberNotParticipated, 펀딩 락 안)
EXPLAIN ANALYZE
SELECT id FROM contributions
WHERE funding_id = 1 AND participant_user_id = 5002 AND status <> 'CANCELLED' LIMIT 1;

-- [보태기] 재시도 요청 확인 (findByFundingIdAndRequestKey, 펀딩 락 안)
EXPLAIN ANALYZE
SELECT * FROM contributions WHERE funding_id = 1 AND request_key = 'seed-1-1';

-- [홈] 내 펀딩 목록 (HomeFundingService)
EXPLAIN ANALYZE
SELECT * FROM fundings WHERE owner_id = 1 AND status IN ('FUNDING', 'CLOSED', 'GIFT_PREPARING');

-- [홈] 펀딩별 보탬 집계 (summarizeByFundingIds)
EXPLAIN ANALYZE
SELECT funding_id,
       SUM(CASE WHEN status = 'CONFIRMED' THEN amount ELSE 0 END),
       SUM(CASE WHEN status = 'PENDING_CONFIRMATION' THEN amount ELSE 0 END),
       COUNT(DISTINCT CASE WHEN status <> 'CANCELLED' THEN participant_user_id END)
FROM contributions
WHERE funding_id IN (1)
GROUP BY funding_id;
