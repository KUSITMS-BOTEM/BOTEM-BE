-- 개설자가 정한 1인 추천 금액(N빵). 정하지 않은 펀딩과 기존 펀딩은 NULL
ALTER TABLE fundings ADD COLUMN recommended_amount BIGINT NULL;
