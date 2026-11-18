ALTER TABLE contributions
    ADD COLUMN request_key VARCHAR(64) NULL AFTER guest_password_hash,
    ADD CONSTRAINT uk_contributions_funding_request_key UNIQUE (funding_id, request_key);
