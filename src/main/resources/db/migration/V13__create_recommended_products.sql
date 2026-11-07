CREATE TABLE recommended_products
(
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    name          VARCHAR(100)  NOT NULL,
    image_url     VARCHAR(2048) NOT NULL,
    price         BIGINT        NOT NULL,
    seller_name   VARCHAR(100)  NOT NULL,
    product_url   VARCHAR(2048) NOT NULL,
    category      VARCHAR(30)   NOT NULL,
    status        VARCHAR(20)   NOT NULL DEFAULT 'ON_SALE',
    display_order INT           NOT NULL DEFAULT 0,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_recommended_products_price_positive CHECK (price > 0),
    INDEX idx_recommended_products_status_category_order (status, category, display_order)
);
