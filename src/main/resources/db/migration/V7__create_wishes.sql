CREATE TABLE wishes
(
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    user_id           BIGINT        NOT NULL,
    product_name      VARCHAR(20)   NOT NULL,
    product_image_url VARCHAR(2048) NOT NULL,
    price             BIGINT        NOT NULL,
    product_url       VARCHAR(2048) NOT NULL,
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_wishes_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_wishes_price_positive CHECK (price > 0),
    INDEX idx_wishes_user_id_created_at (user_id, created_at)
);
