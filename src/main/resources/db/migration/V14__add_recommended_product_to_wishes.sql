ALTER TABLE wishes
    MODIFY COLUMN product_name VARCHAR(100) NOT NULL,
    ADD COLUMN recommended_product_id BIGINT NULL,
    ADD CONSTRAINT fk_wishes_recommended_product FOREIGN KEY (recommended_product_id) REFERENCES recommended_products (id),
    -- 같은 추천 상품을 중복으로 담지 못하도록 막는다. 추천 상품이 아닌 위시(NULL)는 제약을 받지 않는다.
    ADD CONSTRAINT uk_wishes_user_recommended_product UNIQUE (user_id, recommended_product_id);

ALTER TABLE fundings
    MODIFY COLUMN product_name VARCHAR(100) NOT NULL;

ALTER TABLE gift_verifications
    MODIFY COLUMN replacement_product_name VARCHAR(100) NULL;
