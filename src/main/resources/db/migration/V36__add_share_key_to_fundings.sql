-- 공유 링크를 순차 ID 대신 추측할 수 없는 키로 바꾼다.
-- 기존 펀딩은 UUID()(시간 기반이라 추측 가능) 대신 RANDOM_BYTES로 32자리 키를 채운다
ALTER TABLE fundings
    ADD COLUMN share_key VARCHAR(32) NULL AFTER notify_recipient_friends;

UPDATE fundings
SET share_key = LOWER(HEX(RANDOM_BYTES(16)))
WHERE share_key IS NULL;

ALTER TABLE fundings
    MODIFY COLUMN share_key VARCHAR(32) NOT NULL,
    ADD CONSTRAINT uk_fundings_share_key UNIQUE (share_key),
    ADD CONSTRAINT chk_fundings_share_key CHECK (share_key REGEXP '^[0-9a-f]{32}$');
