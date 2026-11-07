ALTER TABLE users
    ADD COLUMN birth_month_day VARCHAR(5)  NULL,
    ADD COLUMN name            VARCHAR(50) NULL;

-- 생일은 연도 없이 월·일(MM-DD)로 저장한다.
-- 배포 중 이전 버전이 기존 birthday 컬럼을 사용하므로, 컬럼 삭제는 이전 버전 종료 후 후속 마이그레이션에서 진행한다.
UPDATE users
SET birth_month_day = DATE_FORMAT(birthday, '%m-%d')
WHERE birthday IS NOT NULL;
