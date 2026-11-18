ALTER TABLE contributions
    ADD COLUMN guest_password_failed_count INT         NOT NULL DEFAULT 0 AFTER guest_password_hash,
    ADD COLUMN guest_password_locked_until DATETIME(6) NULL AFTER guest_password_failed_count;
