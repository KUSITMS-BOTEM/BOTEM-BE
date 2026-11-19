-- 조기 마감 시 선물 준비 없이 바로 공개 대기로 넘어간 서프라이즈를 선물 준비 단계로 되돌린다.
-- 주최자가 남은 확인 대기를 처리하고 선물 준비를 완료해야 공개된다
UPDATE fundings
SET status = 'GIFT_PREPARING'
WHERE funding_type = 'SURPRISE'
  AND status = 'WAITING_REVEAL'
  AND gift_prepared_at IS NULL;
