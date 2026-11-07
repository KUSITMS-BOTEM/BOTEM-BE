CREATE TABLE product_requests
(
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    user_id      BIGINT        NOT NULL,
    product_url  VARCHAR(2048) NOT NULL,
    product_name VARCHAR(100)  NULL,
    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_product_requests_user FOREIGN KEY (user_id) REFERENCES users (id)
);
