CREATE TABLE surprise_thank_cards
(
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    funding_id     BIGINT      NOT NULL,
    sender_user_id BIGINT      NOT NULL,
    message        LONGTEXT    NOT NULL,
    sent_at        DATETIME(6) NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    updated_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_surprise_thank_cards_funding UNIQUE (funding_id),
    CONSTRAINT fk_surprise_thank_cards_funding
        FOREIGN KEY (funding_id) REFERENCES fundings (id),
    CONSTRAINT fk_surprise_thank_cards_sender
        FOREIGN KEY (sender_user_id) REFERENCES users (id),
    CONSTRAINT chk_surprise_thank_cards_message
        CHECK (CHAR_LENGTH(TRIM(message)) > 0)
);
