CREATE TABLE contribution_thank_cards
(
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    contribution_id BIGINT      NOT NULL,
    sender_user_id  BIGINT      NOT NULL,
    message         VARCHAR(50) NOT NULL,
    share_key       VARCHAR(32) NOT NULL,
    sent_at         DATETIME(6) NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_contribution_thank_cards_contribution UNIQUE (contribution_id),
    CONSTRAINT uk_contribution_thank_cards_share_key UNIQUE (share_key),
    CONSTRAINT fk_contribution_thank_cards_contribution
        FOREIGN KEY (contribution_id) REFERENCES contributions (id) ON DELETE CASCADE,
    CONSTRAINT fk_contribution_thank_cards_sender
        FOREIGN KEY (sender_user_id) REFERENCES users (id),
    CONSTRAINT chk_contribution_thank_cards_message
        CHECK (CHAR_LENGTH(TRIM(message)) BETWEEN 1 AND 50),
    CONSTRAINT chk_contribution_thank_cards_share_key
        CHECK (share_key REGEXP '^[0-9a-f]{32}$')
);
