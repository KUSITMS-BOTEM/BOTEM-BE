-- 추천 상품과 연결되지 않은 기존 위시는 판매처를 알 수 없어 NULL로 둔다
ALTER TABLE wishes
    ADD COLUMN seller_name VARCHAR(100) NULL;

UPDATE wishes w
    JOIN recommended_products rp ON w.recommended_product_id = rp.id
SET w.seller_name = rp.seller_name;
