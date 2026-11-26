-- 시드 적재 후 k6가 쓸 ID를 JSON 한 줄로 뽑는다. 자동 증가 ID라 시드만으로는 계산할 수 없는 값들이다
-- docker exec -i botem-mysql mysql -ubotem -pbotem-local botem_loadtest -N -s -r < loadtest/fixtures.sql > loadtest/k6/fixtures.json
SET SESSION group_concat_max_len = 16777216;

SELECT JSON_OBJECT(
    -- 회원 1~1000의 위시 하나와, 첫 번째 친구의 위시 하나
    'wishes', (SELECT JSON_ARRAYAGG(JSON_ARRAY(user_id, wish_id)) FROM (
        SELECT user_id, MIN(id) AS wish_id FROM wishes WHERE user_id <= 1000 GROUP BY user_id) w),
    'friendWishes', (SELECT JSON_ARRAYAGG(JSON_ARRAY(f.user_id, f.friend_id, (SELECT MIN(id) FROM wishes WHERE user_id = f.friend_id))) FROM (
        SELECT user_id, MIN(friend_id) AS friend_id FROM friendships WHERE user_id <= 1000 GROUP BY user_id) f),
    -- 펀딩 개설용: 진행 중 펀딩이 없는 회원 5001~10000의 위시 전부 [회원, 위시]
    'creatableWishes', (SELECT JSON_ARRAYAGG(JSON_ARRAY(user_id, id)) FROM wishes WHERE user_id > 5000),
    -- 입금 확인용: 진행 중 펀딩의 확인 대기 보탬 [펀딩, 보탬] (개설자 = 펀딩 ID)
    'pendingContributions', (SELECT JSON_ARRAYAGG(JSON_ARRAY(funding_id, id)) FROM contributions
        WHERE funding_id <= 5000 AND status = 'PENDING_CONFIRMATION'),
    -- 감사 카드를 받은 참여자 [보탬, 참여자]
    'thankCards', (SELECT JSON_ARRAYAGG(JSON_ARRAY(c.id, c.participant_user_id)) FROM contribution_thank_cards t
        JOIN contributions c ON c.id = t.contribution_id)
);
