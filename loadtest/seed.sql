-- 부하 테스트 전용 DB(botem_loadtest)에 넣는 시드 데이터. 운영·개발 DB에서 실행하지 않는다.
-- users 10,000명, 진행 중 펀딩 5,000개(펀딩당 보탬 50건, 총 250,000건), 동시 보태기용 펀딩 1개
-- 전체 API 측정용(#273): 추천 상품 300개, 회원당 위시 10개·친구 30명·정산 계좌 1개, 보탬마다 롤링페이퍼,
-- 마감된 펀딩 5,000개(보탬 20건·감사 카드 1장), 공개 시각이 지난 서프라이즈 2,000개(보탬 20건)
-- share_key는 k6가 계산할 수 있도록 펀딩 ID를 32자리 16진수로 채운 값이다.

SET SESSION cte_max_recursion_depth = 20000;

-- 계좌번호 암호문은 application.yaml의 로컬 기본 암호화 키로 더미 번호를 암호화한 값이다
SET @encrypted_account = 'K8czqGIVJnOaggz5qoag70FiO516mkETWwVB4q+YygPHr7pF91UFgqw=';

-- wish_profile_key도 k6가 계산할 수 있도록 회원 ID를 32자리 16진수로 채운 값이다
INSERT INTO users (id, kakao_id, nickname, name, status, wish_profile_key, birth_month_day, created_at, updated_at)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 10000)
SELECT n, n, CONCAT('user', n), CONCAT('사용자', n), 'ACTIVE', LPAD(HEX(n), 32, '0'),
       DATE_FORMAT(DATE('2000-01-01') + INTERVAL (n % 365) DAY, '%m-%d'), NOW(6), NOW(6)
FROM seq;

INSERT INTO fundings (
    id, owner_id, funding_type, status, target_amount, deadline, share_key,
    product_name, product_image_url, product_price, product_url,
    payout_bank, payout_encrypted_account_number, created_at, updated_at
)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 5001)
SELECT
    n, n, 'BIRTHDAY', 'FUNDING', 10000000, NOW(6) + INTERVAL 30 DAY, LPAD(HEX(n), 32, '0'),
    '무선 헤드폰', 'https://image.example.com/product.jpg', 300000, 'https://shop.example.com/product',
    'KAKAO_BANK', @encrypted_account, NOW(6), NOW(6)
FROM seq;

-- 펀딩 1~5000에 보탬 50건씩. 절반은 회원(사용자 5001~10000), 절반은 비회원이고 상태를 섞는다.
-- 5001번 펀딩은 동시 보태기 시나리오용이라 비워 둔다
INSERT INTO contributions (
    funding_id, participant_user_id, participant_name, amount, status,
    request_key, confirmed_at, created_at, updated_at
)
WITH RECURSIVE
    funding_seq (f) AS (SELECT 1 UNION ALL SELECT f + 1 FROM funding_seq WHERE f < 5000),
    slot_seq (s) AS (SELECT 1 UNION ALL SELECT s + 1 FROM slot_seq WHERE s < 50)
SELECT
    f,
    IF(s % 2 = 0, 5000 + ((f * 50 + s) % 5000) + 1, NULL),
    CONCAT('참여자', s),
    10000,
    ELT(s % 3 + 1, 'CONFIRMED', 'PENDING_CONFIRMATION', 'AWAITING_TRANSFER'),
    CONCAT('seed-', f, '-', s),
    IF(s % 3 = 0, NOW(6), NULL),
    NOW(6) - INTERVAL (50 - s) MINUTE,
    NOW(6)
FROM funding_seq
CROSS JOIN slot_seq;

-- 추천 상품 300개 (카테고리 6개를 돌아가며)
INSERT INTO recommended_products (id, name, image_url, price, seller_name, product_url, category, display_order, created_at, updated_at)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 300)
SELECT n, CONCAT('추천 상품', n), 'https://image.example.com/product.jpg', 10000 + n * 1000, 'BOTEM 브랜드샵',
       CONCAT('https://shop.example.com/product/', n),
       ELT(n % 6 + 1, 'EARPHONE_HEADPHONE', 'SMART_DEVICE_GAME', 'HAIR_BEAUTY_DEVICE', 'SKINCARE', 'PERFUME', 'LIVING_APPLIANCE'),
       n, NOW(6), NOW(6)
FROM seq;

-- 회원당 위시 10개. 회원마다 서로 다른 추천 상품을 담는다
INSERT INTO wishes (user_id, product_name, product_image_url, price, product_url, seller_name, recommended_product_id, created_at, updated_at)
WITH RECURSIVE
    user_seq (u) AS (SELECT 1 UNION ALL SELECT u + 1 FROM user_seq WHERE u < 10000),
    slot_seq (s) AS (SELECT 1 UNION ALL SELECT s + 1 FROM slot_seq WHERE s < 10)
SELECT u, CONCAT('추천 상품', (u + s * 29) % 300 + 1), 'https://image.example.com/product.jpg', 50000,
       'https://shop.example.com/product', 'BOTEM 브랜드샵', (u + s * 29) % 300 + 1,
       NOW(6) - INTERVAL s MINUTE, NOW(6)
FROM user_seq CROSS JOIN slot_seq;

-- 회원당 친구 30명 (한 방향씩 저장)
INSERT INTO friendships (user_id, friend_id, created_at, updated_at)
WITH RECURSIVE
    user_seq (u) AS (SELECT 1 UNION ALL SELECT u + 1 FROM user_seq WHERE u < 10000),
    slot_seq (s) AS (SELECT 1 UNION ALL SELECT s + 1 FROM slot_seq WHERE s < 30)
SELECT u, (u + s * 37 - 1) % 10000 + 1, NOW(6), NOW(6)
FROM user_seq CROSS JOIN slot_seq;

INSERT INTO payout_accounts (user_id, bank, encrypted_account_number, is_default, created_at, updated_at)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 10000)
SELECT n, 'KAKAO_BANK', @encrypted_account, TRUE, NOW(6), NOW(6)
FROM seq;

-- 마감된 생일 펀딩 5002~10001 (개설자 1~5000). 5002~7501은 결과를 열어 선물 준비 중(롤링페이퍼 조회 가능)
-- 공개 시각이 지난 서프라이즈 10002~12001 (개설자 1~2000, 받는 사람 5001~7000). 11002~12001은 받는 사람이 연 상태
INSERT INTO fundings (
    id, owner_id, recipient_id, funding_type, status, target_amount, deadline, reveal_at, share_key,
    product_name, product_image_url, product_price, product_url,
    payout_bank, payout_encrypted_account_number, result_opened_at, surprise_opened_at, created_at, updated_at
)
WITH RECURSIVE seq (n) AS (SELECT 5002 UNION ALL SELECT n + 1 FROM seq WHERE n < 12001)
SELECT
    n,
    IF(n <= 10001, n - 5001, n - 10001),
    IF(n <= 10001, NULL, n - 10001 + 5000),
    IF(n <= 10001, 'BIRTHDAY', 'SURPRISE'),
    CASE WHEN n <= 7501 THEN 'GIFT_PREPARING' WHEN n <= 10001 THEN 'CLOSED' WHEN n <= 11001 THEN 'WAITING_REVEAL' ELSE 'REVEALED' END,
    300000, NOW(6) - INTERVAL 3 DAY,
    IF(n <= 10001, NULL, NOW(6) - INTERVAL 1 DAY),
    LPAD(HEX(n), 32, '0'),
    '무선 헤드폰', 'https://image.example.com/product.jpg', 300000, 'https://shop.example.com/product',
    'KAKAO_BANK', @encrypted_account,
    IF(n <= 7501, NOW(6) - INTERVAL 1 DAY, NULL),
    IF(n > 11001, NOW(6) - INTERVAL 12 HOUR, NULL),
    NOW(6) - INTERVAL 10 DAY, NOW(6)
FROM seq;

-- 마감·서프라이즈 펀딩마다 확정된 회원 보탬 20건
INSERT INTO contributions (
    funding_id, participant_user_id, participant_name, amount, status,
    request_key, confirmed_at, created_at, updated_at
)
WITH RECURSIVE
    funding_seq (f) AS (SELECT 5002 UNION ALL SELECT f + 1 FROM funding_seq WHERE f < 12001),
    slot_seq (s) AS (SELECT 1 UNION ALL SELECT s + 1 FROM slot_seq WHERE s < 20)
SELECT f, 7000 + ((f * 20 + s) % 3000) + 1, CONCAT('참여자', s), 15000, 'CONFIRMED',
       CONCAT('closed-', f, '-', s), NOW(6) - INTERVAL 4 DAY, NOW(6) - INTERVAL 5 DAY, NOW(6)
FROM funding_seq CROSS JOIN slot_seq;

-- 보탬마다 롤링페이퍼 메시지 1개
INSERT INTO rolling_paper_messages (contribution_id, content, paper_template_code, created_at, updated_at)
SELECT id, '생일 축하해! 갖고 싶던 선물 꼭 받길 바라 🎉', 'LIME_DOT_GRID', created_at, updated_at
FROM contributions;

-- 마감된 생일 펀딩의 첫 보탬에 감사 카드. share_key는 펀딩 ID를 32자리 16진수로 채운 값이다
INSERT INTO contribution_thank_cards (contribution_id, sender_user_id, message, share_key, sent_at, created_at, updated_at)
SELECT c.id, f.owner_id, '보태 줘서 고마워!', LPAD(HEX(f.id), 32, '0'), NOW(6), NOW(6), NOW(6)
FROM contributions c
JOIN fundings f ON f.id = c.funding_id
WHERE f.funding_type = 'BIRTHDAY' AND f.id BETWEEN 5002 AND 10001 AND c.request_key = CONCAT('closed-', f.id, '-1');

ANALYZE TABLE users, fundings, contributions, recommended_products, wishes, friendships, payout_accounts,
    rolling_paper_messages, contribution_thank_cards;
