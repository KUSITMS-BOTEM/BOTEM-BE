ALTER TABLE users
    ADD COLUMN wish_profile_key CHAR(32) NULL;

UPDATE users
SET wish_profile_key = REPLACE(UUID(), '-', '')
WHERE wish_profile_key IS NULL;

ALTER TABLE users
    MODIFY COLUMN wish_profile_key CHAR(32) NOT NULL,
    ADD CONSTRAINT uk_users_wish_profile_key UNIQUE (wish_profile_key);
