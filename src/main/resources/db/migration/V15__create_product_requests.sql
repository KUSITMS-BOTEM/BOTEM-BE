CREATE TABLE product_requests
(
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    user_id            BIGINT        NOT NULL,
    product_url        VARCHAR(2048) NOT NULL,
    -- 같은 상품이라도 링크의 쿼리가 달라지므로 상품 ID로 요청 수를 집계한다. 단축 링크는 상품 ID를 알 수 없어 NULL이다.
    coupang_product_id VARCHAR(20)   NULL,
    product_name       VARCHAR(100)  NULL,
    created_at         DATETIME(6)   NOT NULL,
    updated_at         DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_product_requests_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_product_requests_coupang_product_id (coupang_product_id)
);
