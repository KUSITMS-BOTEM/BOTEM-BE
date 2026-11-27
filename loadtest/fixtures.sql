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
    -- 회원당 진행 중 펀딩은 5개까지라, 서버의 진행 중 판정과 같은 조건으로 남은 자리만큼만 위시를 고른다
    'creatableWishes', (SELECT JSON_ARRAYAGG(JSON_ARRAY(user_id, id)) FROM (
        SELECT w.user_id, w.id,
               ROW_NUMBER() OVER (PARTITION BY w.user_id ORDER BY w.id) AS wish_rank,
               5 - COALESCE(active.funding_count, 0) AS open_slots
        FROM wishes w
        LEFT JOIN (
            SELECT owner_id, COUNT(*) AS funding_count FROM fundings
            WHERE (funding_type = 'BIRTHDAY' AND status IN ('FUNDING', 'CLOSED') AND deadline > NOW())
               OR (funding_type = 'SURPRISE' AND status IN ('FUNDING', 'CLOSED', 'GIFT_PREPARING', 'WAITING_REVEAL')
                   AND reveal_at > NOW())
            GROUP BY owner_id
        ) active ON active.owner_id = w.user_id
    ) slots WHERE wish_rank <= open_slots),
    -- 입금 확인용: 진행 중 펀딩의 확인 대기 보탬 [펀딩, 보탬] (개설자 = 펀딩 ID)
    'pendingContributions', (SELECT JSON_ARRAYAGG(JSON_ARRAY(funding_id, id)) FROM contributions
        WHERE funding_id <= 5000 AND status = 'PENDING_CONFIRMATION'),
    -- 감사 카드를 받은 참여자 [보탬, 참여자]
    'thankCards', (SELECT JSON_ARRAYAGG(JSON_ARRAY(c.id, c.participant_user_id)) FROM contribution_thank_cards t
        JOIN contributions c ON c.id = t.contribution_id)
);
