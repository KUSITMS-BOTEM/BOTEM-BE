ALTER TABLE fundings
    ADD COLUMN early_closed_at DATETIME(6) NULL,
    ADD COLUMN product_changed_at DATETIME(6) NULL;

CREATE TABLE funding_product_change_histories
(
    id                         BIGINT        NOT NULL AUTO_INCREMENT,
    funding_id                 BIGINT        NOT NULL,
    previous_source_wish_id    BIGINT        NULL,
    previous_product_name      VARCHAR(100)  NOT NULL,
    previous_product_image_url VARCHAR(2048) NOT NULL,
    previous_product_price     BIGINT        NOT NULL,
    previous_product_url       VARCHAR(2048) NOT NULL,
    changed_source_wish_id     BIGINT        NULL,
    changed_product_name       VARCHAR(100)  NOT NULL,
    changed_product_image_url  VARCHAR(2048) NOT NULL,
    changed_product_price      BIGINT        NOT NULL,
    changed_product_url        VARCHAR(2048) NOT NULL,
    changed_at                 DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_funding_product_changes_funding
        FOREIGN KEY (funding_id) REFERENCES fundings (id) ON DELETE CASCADE,
    CONSTRAINT chk_funding_product_changes_previous_price CHECK (previous_product_price > 0),
    CONSTRAINT chk_funding_product_changes_changed_price CHECK (changed_product_price > 0),
    INDEX idx_funding_product_changes_funding_changed (funding_id, changed_at)
);
