import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { SharedArray } from 'k6/data';
import { BASE_URL, shareKeyOf, randomInt, accessTokenOf } from './common.js';

// API별 기준 측정 (#273). API마다 같은 부하(VUS명, DURATION)를 차례로 걸어 p95·처리량을 한 표로 비교한다.
// 조회는 관리자 API를 뺀 전체, 쓰기는 사용자가 자주 하는 것(위시 담기·빼기, 펀딩 개설, 회원 보태기, 입금 확인)만 잰다
// 2MB가 넘는 ID 목록을 VU마다 따로 파싱하면 k6가 메모리·CPU를 크게 써 측정이 왜곡된다. 목록별로 한 번만 읽어 모든 VU가 공유한다
const shared = (name, transform = (list) => list) =>
    new SharedArray(name, () => transform(JSON.parse(open('./fixtures.json'))[name]));
const fixtures = {
    wishes: shared('wishes'),
    friendWishes: shared('friendWishes'),
    pendingContributions: shared('pendingContributions'),
    thankCards: shared('thankCards'),
};
const VUS = Number(__ENV.VUS || 20);
const DURATION = __ENV.DURATION || '15s';
// 처리량 계산과 구간 일정에 초 단위 값이 필요해 '15s'처럼 초 단위만 받는다
if (!/^\d+s$/.test(DURATION)) {
    throw new Error(`DURATION은 초 단위로 지정하세요 (예: 15s). 입력값: ${DURATION}`);
}
const DURATION_SECONDS = Number(DURATION.slice(0, -1));
// 구간이 끝난 뒤 남은 요청이 다음 API 측정과 겹치지 않도록, 마무리 대기 시간까지 지난 뒤 다음 구간을 시작한다
const GRACEFUL_STOP_SECONDS = 5;
const GAP_SECONDS = 2;

// 개설용 위시와 확인 대기 보탬은 한 번 쓰면 상태가 바뀐다. 다 쓰면 같은 항목을 다시 쓰지 않고 측정을 멈춘다
function takeOnce(list, label) {
    const index = exec.scenario.iterationInTest;
    if (index >= list.length) {
        exec.test.abort(`${label} 목록을 모두 사용했습니다. 시드와 fixtures.json을 다시 만든 뒤 실행하세요.`);
    }
    return list[index];
}

const pick = (list) => list[randomInt(0, list.length - 1)];

// 회원별 남은 개설 자리만큼 뽑은 위시(fixtures.sql)를, 같은 회원이 연달아 나오지 않도록 회원을 번갈아 가며 줄 세운다
const creatableWishes = shared('creatableWishes', (list) => {
    const seen = {};
    return list
        .map(([userId, wishId]) => ({ userId, wishId, rank: (seen[userId] = (seen[userId] || 0) + 1) }))
        .sort((a, b) => a.rank - b.rank || a.userId - b.userId);
});
const authed = (userId, name) => ({
    headers: { Authorization: `Bearer ${accessTokenOf(userId)}`, 'Content-Type': 'application/json' },
    tags: { name },
});
const publicReq = (name) => ({ headers: { 'Content-Type': 'application/json' }, tags: { name } });

// 진행 중 펀딩 1~5000(개설자 = 펀딩 ID), 결과를 연 생일 펀딩 5002~7501, 연 서프라이즈 11002~12001(개설자 1~1000·받는 사람 6001~7000)
const endpoints = {
    'GET /api/home/fundings': () => http.get(`${BASE_URL}/api/home/fundings`, authed(randomInt(1, 5000), 'GET /api/home/fundings')),
    'GET /api/home/gift-verifications': () => http.get(`${BASE_URL}/api/home/gift-verifications`, authed(randomInt(1, 10000), 'GET /api/home/gift-verifications')),
    'GET /api/home/gift-verifications/banner': () => http.get(`${BASE_URL}/api/home/gift-verifications/banner`, authed(randomInt(1, 10000), 'GET /api/home/gift-verifications/banner')),
    'GET /api/home/no-funding': () => http.get(`${BASE_URL}/api/home/no-funding`, authed(randomInt(7001, 10000), 'GET /api/home/no-funding')),
    'GET /api/home/surprise-reveals': () => http.get(`${BASE_URL}/api/home/surprise-reveals`, authed(randomInt(5001, 7000), 'GET /api/home/surprise-reveals')),
    'GET /api/friends': () => http.get(`${BASE_URL}/api/friends`, authed(randomInt(1, 10000), 'GET /api/friends')),
    'GET /api/friends/{friendId}/profile': () => {
        const [userId, friendId] = pick(fixtures.friendWishes);
        return http.get(`${BASE_URL}/api/friends/${friendId}/profile`, authed(userId, 'GET /api/friends/{friendId}/profile'));
    },
    'GET /api/friends/{friendId}/wishes/{wishId}': () => {
        const [userId, friendId, wishId] = pick(fixtures.friendWishes);
        return http.get(`${BASE_URL}/api/friends/${friendId}/wishes/${wishId}`, authed(userId, 'GET /api/friends/{friendId}/wishes/{wishId}'));
    },
    'GET /api/fundings/share/{shareKey}': () => http.get(`${BASE_URL}/api/fundings/share/${shareKeyOf(randomInt(1, 5000))}`, publicReq('GET /api/fundings/share/{shareKey}')),
    'GET /api/fundings/share/{shareKey}/contributions/me': () => {
        // 진행 중 펀딩의 회원 참여자는 5001~10000
        return http.get(`${BASE_URL}/api/fundings/share/${shareKeyOf(randomInt(1, 5000))}/contributions/me`,
            authed(randomInt(5001, 10000), 'GET /api/fundings/share/{shareKey}/contributions/me'));
    },
    'GET /api/fundings/{fundingId}/management': () => {
        const fundingId = randomInt(1, 5000);
        return http.get(`${BASE_URL}/api/fundings/${fundingId}/management`, authed(fundingId, 'GET /api/fundings/{fundingId}/management'));
    },
    'GET /api/fundings/{fundingId}/contributions/pending': () => {
        const fundingId = randomInt(1, 5000);
        return http.get(`${BASE_URL}/api/fundings/${fundingId}/contributions/pending`, authed(fundingId, 'GET /api/fundings/{fundingId}/contributions/pending'));
    },
    'GET /api/fundings/{fundingId}/rolling-paper': () => {
        const fundingId = randomInt(5002, 7501);
        return http.get(`${BASE_URL}/api/fundings/${fundingId}/rolling-paper`, authed(fundingId - 5001, 'GET /api/fundings/{fundingId}/rolling-paper'));
    },
    'GET /api/fundings/{fundingId}/result': () => {
        const fundingId = randomInt(5002, 10001);
        return http.get(`${BASE_URL}/api/fundings/${fundingId}/result`, authed(fundingId - 5001, 'GET /api/fundings/{fundingId}/result'));
    },
    'GET /api/fundings/{fundingId}/surprise': () => {
        const fundingId = randomInt(10002, 12001);
        return http.get(`${BASE_URL}/api/fundings/${fundingId}/surprise`, authed(fundingId - 10001 + 5000, 'GET /api/fundings/{fundingId}/surprise'));
    },
    'GET /api/fundings/{fundingId}/surprise/gift-preparation': () => {
        const fundingId = randomInt(10002, 12001);
        return http.get(`${BASE_URL}/api/fundings/${fundingId}/surprise/gift-preparation`, authed(fundingId - 10001, 'GET /api/fundings/{fundingId}/surprise/gift-preparation'));
    },
    'GET /api/fundings/{fundingId}/surprise/thank-card-friends': () => {
        const fundingId = randomInt(11002, 12001);
        return http.get(`${BASE_URL}/api/fundings/${fundingId}/surprise/thank-card-friends`, authed(fundingId - 10001 + 5000, 'GET /api/fundings/{fundingId}/surprise/thank-card-friends'));
    },
    'GET /api/recommended-products': () => http.get(`${BASE_URL}/api/recommended-products`, authed(randomInt(1, 10000), 'GET /api/recommended-products')),
    'GET /api/recommended-products/{productId}': () => http.get(`${BASE_URL}/api/recommended-products/${randomInt(1, 300)}`, authed(randomInt(1, 10000), 'GET /api/recommended-products/{productId}')),
    'GET /api/thank-cards/{shareKey}': () => http.get(`${BASE_URL}/api/thank-cards/${shareKeyOf(randomInt(5002, 10001))}`, publicReq('GET /api/thank-cards/{shareKey}')),
    'GET /api/contributions/{contributionId}/thank-card': () => {
        const [contributionId, userId] = pick(fixtures.thankCards);
        return http.get(`${BASE_URL}/api/contributions/${contributionId}/thank-card`, authed(userId, 'GET /api/contributions/{contributionId}/thank-card'));
    },
    'GET /api/users/me/fundings/closed': () => http.get(`${BASE_URL}/api/users/me/fundings/closed`, authed(randomInt(1, 5000), 'GET /api/users/me/fundings/closed')),
    'GET /api/users/me/payout-accounts': () => http.get(`${BASE_URL}/api/users/me/payout-accounts`, authed(randomInt(1, 10000), 'GET /api/users/me/payout-accounts')),
    'GET /api/users/me/profile': () => http.get(`${BASE_URL}/api/users/me/profile`, authed(randomInt(1, 10000), 'GET /api/users/me/profile')),
    'GET /api/wish-profiles/{wishProfileKey}': () => http.get(`${BASE_URL}/api/wish-profiles/${shareKeyOf(randomInt(1, 10000))}`, publicReq('GET /api/wish-profiles/{wishProfileKey}')),
    'GET /api/wishes': () => http.get(`${BASE_URL}/api/wishes`, authed(randomInt(1, 10000), 'GET /api/wishes')),
    'GET /api/wishes/{wishId}': () => {
        const [userId, wishId] = pick(fixtures.wishes);
        return http.get(`${BASE_URL}/api/wishes/${wishId}`, authed(userId, 'GET /api/wishes/{wishId}'));
    },
    // 쓰기: 위시를 담고 바로 뺀다
    'POST·DELETE /api/wishes': () => {
        const userId = randomInt(1, 10000);
        // 이미 담은 상품을 고르면 409가 정상이라 실패로 세지 않는다
        const created = http.post(`${BASE_URL}/api/wishes`, JSON.stringify({ recommendedProductId: randomInt(1, 300) }),
            { ...authed(userId, 'POST /api/wishes'), responseCallback: http.expectedStatuses({ min: 200, max: 299 }, 409) });
        if (created.status === 201) {
            http.del(`${BASE_URL}/api/wishes/${created.json('data.id')}`, null, authed(userId, 'DELETE /api/wishes/{wishId}'));
        }
        return created;
    },
    // 쓰기: 진행 중 펀딩이 없는 회원의 위시로 개설한다. 반복마다 다른 위시를 써서 같은 위시로 두 번 열지 않는다
    'POST /api/fundings': () => {
        const { userId, wishId } = takeOnce(creatableWishes, '개설용 위시');
        const deadline = new Date(Date.now() + 7 * 24 * 3600 * 1000).toISOString().slice(0, 10);
        return http.post(`${BASE_URL}/api/fundings`, JSON.stringify({
            wishId, bank: 'KAKAO_BANK', accountNumber: '3333012345678', targetAmount: 300000, deadline,
            story: '부하 테스트용 펀딩이에요', recommendedAmount: 30000,
        }), authed(userId, 'POST /api/fundings'));
    },
    // 쓰기: 회원 보태기. 진행 중 펀딩 1~5000에 참여 이력이 없는 회원 1~5000이 보탠다 (개설자 본인 펀딩은 피한다)
    'POST /api/fundings/share/{shareKey}/contributions': () => {
        const fundingId = randomInt(1, 5000);
        const userId = (fundingId + randomInt(1, 4998)) % 5000 + 1;
        // 무작위 회원·펀딩 조합이 겹치면 중복 참여 409라 실패로 세지 않는다
        return http.post(`${BASE_URL}/api/fundings/share/${shareKeyOf(fundingId)}/contributions`, JSON.stringify({
            requestKey: `baseline-${exec.scenario.iterationInTest}-${Date.now()}`,
            amount: 10000, message: '생일 축하해!', paperTemplateCode: 'LIME_DOT_GRID',
        }), { ...authed(userId, 'POST /api/fundings/share/{shareKey}/contributions'), responseCallback: http.expectedStatuses(201, 409) });
    },
    // 쓰기: 개설자의 입금 확인. 확인 대기 보탬을 하나씩 확정한다
    'POST /api/fundings/{fundingId}/contributions/{contributionId}/confirm': () => {
        const [fundingId, contributionId] = takeOnce(fixtures.pendingContributions, '확인 대기 보탬');
        return http.post(`${BASE_URL}/api/fundings/${fundingId}/contributions/${contributionId}/confirm`, null,
            authed(fundingId, 'POST /api/fundings/{fundingId}/contributions/{contributionId}/confirm'));
    },
};

const names = Object.keys(endpoints);

export const options = {
    scenarios: Object.fromEntries(names.map((name, index) => [`s${String(index).padStart(2, '0')}`, {
        executor: 'constant-vus',
        vus: VUS,
        duration: DURATION,
        gracefulStop: `${GRACEFUL_STOP_SECONDS}s`,
        startTime: `${index * (DURATION_SECONDS + GRACEFUL_STOP_SECONDS + GAP_SECONDS)}s`,
        exec: 'run',
        env: { ENDPOINT: name },
        tags: { endpoint: name },
    }])),
    // 태그별 지표를 요약에 남기려면 태그마다 임계값이 있어야 한다
    thresholds: Object.fromEntries(names.flatMap((name) => [
        [`http_req_duration{endpoint:${name}}`, ['p(95)<300']],
        [`http_req_failed{endpoint:${name}}`, ['rate<0.01']],
    ])),
    summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

export function run() {
    const res = endpoints[__ENV.ENDPOINT]();
    check(res, { 'status 2xx': (r) => r.status >= 200 && r.status < 300 });
}

// API마다 한 줄씩 평균·p95·p99·처리량·실패율 표를 출력한다
export function handleSummary(data) {
    const rows = names.map((name) => {
        const duration = data.metrics[`http_req_duration{endpoint:${name}}`]?.values || {};
        const failed = data.metrics[`http_req_failed{endpoint:${name}}`]?.values || {};
        const count = (failed.passes || 0) + (failed.fails || 0);
        return `| ${name} | ${(count / DURATION_SECONDS).toFixed(0)} | ${(duration.avg || 0).toFixed(1)} | ${(duration['p(95)'] || 0).toFixed(1)} | ${(duration['p(99)'] || 0).toFixed(1)} | ${((failed.rate || 0) * 100).toFixed(2)}% |`;
    });
    const table = ['| API | req/s | 평균(ms) | p95(ms) | p99(ms) | 실패율 |', '|---|---|---|---|---|---|', ...rows].join('\n');
    return { stdout: `\n${table}\n`, [__ENV.SUMMARY_FILE || 'api-baseline-summary.md']: table };
}
