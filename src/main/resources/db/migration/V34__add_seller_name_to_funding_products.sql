-- 선물 준비 화면 상품 카드의 판매자 이름. 기존 펀딩은 지금 상품의 원본 위시에서 채우고, 알 수 없으면 NULL로 둔다
ALTER TABLE fundings
    ADD COLUMN product_seller_name VARCHAR(100) NULL AFTER product_url;

UPDATE fundings f
    JOIN wishes w ON f.source_wish_id = w.id
SET f.product_seller_name = w.seller_name;
