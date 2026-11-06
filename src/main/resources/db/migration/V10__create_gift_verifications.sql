CREATE TABLE gift_verifications
(
    id                           BIGINT        NOT NULL AUTO_INCREMENT,
    funding_id                   BIGINT        NOT NULL,
    status                       VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    image_url                    VARCHAR(2048) NULL,
    thank_you_message            TEXT          NULL,
    purchased_through_botem_link BOOLEAN       NOT NULL DEFAULT FALSE,
    replacement_product_name     VARCHAR(20)   NULL,
    completed_at                 DATETIME(6)   NULL,
    created_at                   DATETIME(6)   NOT NULL,
    updated_at                   DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_gift_verifications_funding UNIQUE (funding_id),
    CONSTRAINT fk_gift_verifications_funding FOREIGN KEY (funding_id) REFERENCES fundings (id),
    CONSTRAINT chk_gift_verifications_completion CHECK (
        (status = 'PENDING' AND completed_at IS NULL)
        OR (
            status = 'COMPLETED'
            AND image_url IS NOT NULL
            AND purchased_through_botem_link = TRUE
            AND completed_at IS NOT NULL
        )
    ),
    INDEX idx_gift_verifications_status_created_at (status, created_at)
);
