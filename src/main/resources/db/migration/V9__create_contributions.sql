CREATE TABLE contributions
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    funding_id          BIGINT       NOT NULL,
    participant_user_id BIGINT       NULL,
    participant_name    VARCHAR(50)  NOT NULL,
    amount              BIGINT       NOT NULL,
    status              VARCHAR(30)  NOT NULL,
    guest_password_hash VARCHAR(255) NULL,
    confirmed_at        DATETIME(6)  NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_contributions_funding FOREIGN KEY (funding_id) REFERENCES fundings (id),
    CONSTRAINT fk_contributions_participant_user FOREIGN KEY (participant_user_id) REFERENCES users (id),
    CONSTRAINT chk_contributions_amount CHECK (amount >= 0),
    CONSTRAINT chk_contributions_message_only CHECK (
        (status = 'MESSAGE_ONLY' AND amount = 0)
        OR (status <> 'MESSAGE_ONLY' AND amount > 0)
    ),
    CONSTRAINT chk_contributions_confirmed_at CHECK (
        (status = 'CONFIRMED' AND confirmed_at IS NOT NULL)
        OR (status <> 'CONFIRMED' AND confirmed_at IS NULL)
    ),
    INDEX idx_contributions_funding_status (funding_id, status),
    INDEX idx_contributions_participant_user (participant_user_id, created_at)
);
