-- '못 받았어요' 실행 취소 허용 시간을 판단하려고 처리 시각을 남긴다.
-- 기존 REJECTED 보탬은 처리 시각을 알 수 없으므로 NULL로 두어 실행 취소 대상에서 제외한다
ALTER TABLE contributions
    ADD COLUMN rejected_at DATETIME(6) NULL AFTER confirmed_at;
