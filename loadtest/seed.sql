-- 부하 테스트 전용 DB(botem_loadtest)에 넣는 시드 데이터. 운영·개발 DB에서 실행하지 않는다.
-- users 10,000명, 진행 중 펀딩 5,000개(펀딩당 보탬 50건, 총 250,000건), 동시 보태기용 펀딩 1개
-- share_key는 k6가 계산할 수 있도록 펀딩 ID를 32자리 16진수로 채운 값이다.

SET SESSION cte_max_recursion_depth = 20000;

-- 계좌번호 암호문은 application.yaml의 로컬 기본 암호화 키로 더미 번호를 암호화한 값이다
SET @encrypted_account = 'K8czqGIVJnOaggz5qoag70FiO516mkETWwVB4q+YygPHr7pF91UFgqw=';

INSERT INTO users (id, kakao_id, nickname, name, status, created_at, updated_at)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 10000)
SELECT n, n, CONCAT('user', n), CONCAT('사용자', n), 'ACTIVE', NOW(6), NOW(6)
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

ANALYZE TABLE users, fundings, contributions;
