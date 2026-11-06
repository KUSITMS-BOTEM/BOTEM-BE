CREATE TABLE payout_accounts
(
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    user_id                  BIGINT       NOT NULL,
    bank                     VARCHAR(30)  NOT NULL,
    encrypted_account_number VARCHAR(255) NOT NULL,
    account_holder           VARCHAR(100) NULL,
    is_default               BOOLEAN      NOT NULL DEFAULT FALSE,
    verification_status      VARCHAR(20)  NOT NULL DEFAULT 'UNVERIFIED',
    created_at               DATETIME(6)  NOT NULL,
    updated_at               DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_payout_accounts_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_payout_accounts_user_bank UNIQUE (user_id, bank)
);
