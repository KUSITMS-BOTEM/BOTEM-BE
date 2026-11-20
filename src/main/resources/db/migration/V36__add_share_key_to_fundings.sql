-- 공유 링크를 순차 ID 대신 추측할 수 없는 키로 바꾼다.
-- 기존 펀딩은 UUID()(시간 기반이라 추측 가능) 대신 RANDOM_BYTES로 32자리 키를 채운다
ALTER TABLE fundings
    ADD COLUMN share_key VARCHAR(32) NULL AFTER notify_recipient_friends;

UPDATE fundings
SET share_key = LOWER(HEX(RANDOM_BYTES(16)))
WHERE share_key IS NULL;

-- 배포 전환 중이나 롤백 후 이전 버전 서버가 share_key 없이 펀딩을 저장해도 실패하지 않도록,
-- 행마다 새로 계산되는 랜덤 기본값을 둔다 (고정 기본값은 UNIQUE와 충돌하므로 사용하지 않는다)
ALTER TABLE fundings
    MODIFY COLUMN share_key VARCHAR(32) NOT NULL DEFAULT (LOWER(HEX(RANDOM_BYTES(16)))),
    ADD CONSTRAINT uk_fundings_share_key UNIQUE (share_key),
    ADD CONSTRAINT chk_fundings_share_key CHECK (share_key REGEXP '^[0-9a-f]{32}$');
