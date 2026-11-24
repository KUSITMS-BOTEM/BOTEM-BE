-- 서프라이즈는 선물 인증 없이 수령자의 선물 선택으로 완료한다.
-- 이전 배포에서 인증 대기로 남은 서프라이즈도 완료 처리해 홈 인증 대상에서 제거한다.
UPDATE fundings
SET status = 'COMPLETED',
    completed_at = COALESCE(completed_at, CURRENT_TIMESTAMP(6))
WHERE funding_type = 'SURPRISE'
  AND status = 'WAITING_GIFT_VERIFICATION';
