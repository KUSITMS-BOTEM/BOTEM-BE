ALTER TABLE users
    ADD COLUMN wish_profile_key VARCHAR(32) NULL;

-- 이전 애플리케이션 버전도 배포 중 신규 회원을 생성할 수 있으므로 당분간 NULL을 허용한다.

UPDATE users
SET wish_profile_key = REPLACE(UUID(), '-', '')
WHERE wish_profile_key IS NULL;

ALTER TABLE users
    ADD CONSTRAINT uk_users_wish_profile_key UNIQUE (wish_profile_key);
