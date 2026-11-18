-- 실패 횟수는 임시 보안 데이터이므로 기여 단위 구조로 전환하면서 초기화한다.
DELETE FROM guest_contribution_access_attempts;

ALTER TABLE guest_contribution_access_attempts
    DROP FOREIGN KEY fk_guest_contribution_access_funding,
    DROP INDEX uk_guest_contribution_access_funding_name,
    DROP COLUMN funding_id,
    DROP COLUMN participant_name,
    ADD COLUMN contribution_id BIGINT NOT NULL AFTER id,
    ADD CONSTRAINT fk_guest_contribution_access_contribution
        FOREIGN KEY (contribution_id) REFERENCES contributions (id),
    ADD CONSTRAINT uk_guest_contribution_access_contribution UNIQUE (contribution_id);
