import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { SharedArray } from 'k6/data';
import { BASE_URL, shareKeyOf, randomInt, accessTokenOf } from './common.js';

// 사용자 흐름 혼합 시나리오 (#273). 실제 사용처럼 여러 화면을 섞어 동시 사용자를 단계적으로 늘리고,
// 단계마다 p95가 어떻게 변하는지로 이 장비의 한계 지점을 찾는다.
// - 참여자 55%: 공유 링크로 펀딩 상세 → 그중 30%는 회원으로 보태기
// - 홈 사용자 30%: 홈 펀딩·서프라이즈·인증 배너 → 위시 목록 → 추천 상품 → 친구 목록
// - 개설자 15%: 관리 상세 → 확인 대기 목록 → 입금 확인 1건
// 2MB가 넘는 ID 목록을 VU마다 따로 파싱하면 k6가 메모리·CPU를 크게 써 측정이 왜곡된다. 목록별로 한 번만 읽어 모든 VU가 공유한다
const pendingContributions = new SharedArray('pendingContributions', () => JSON.parse(open('./fixtures.json')).pendingContributions);
const STAGE_SECONDS = Number(__ENV.STAGE_SECONDS || 60);
const LEVELS = (__ENV.LEVELS || '50,100,200,400,600').split(',').map(Number);
const RAMP_SECONDS = 15;

const stages = LEVELS.flatMap((target) => [
    { duration: `${RAMP_SECONDS}s`, target },
    { duration: `${STAGE_SECONDS}s`, target },
]);

// 요청이 어느 단계에서 나갔는지 태그로 남겨 단계별 p95를 따로 본다
function currentLevel() {
    const elapsed = exec.instance.currentTestRunDuration / 1000;
    const index = Math.min(Math.floor(elapsed / (RAMP_SECONDS + STAGE_SECONDS)), LEVELS.length - 1);
    return String(LEVELS[index]);
}

function params(userId, name) {
    const headers = { 'Content-Type': 'application/json' };
    if (userId) {
        headers.Authorization = `Bearer ${accessTokenOf(userId)}`;
    }
    return { headers, tags: { name, level: currentLevel() } };
}

export const options = {
    scenarios: { flow: { executor: 'ramping-vus', startVUs: 0, stages, gracefulRampDown: '10s' } },
    thresholds: Object.fromEntries(LEVELS.flatMap((level) => [
        [`http_req_duration{level:${level}}`, ['p(95)<300']],
        [`http_req_failed{level:${level}}`, ['rate<0.01']],
        [`http_reqs{level:${level}}`, ['count>0']],
    ])),
    summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

function participant() {
    const fundingId = randomInt(1, 5000);
    const detail = http.get(`${BASE_URL}/api/fundings/share/${shareKeyOf(fundingId)}`, params(null, 'GET /api/fundings/share/{shareKey}'));
    check(detail, { 'detail 200': (r) => r.status === 200 });
    if (Math.random() < 0.3) {
        sleep(1);
        // 진행 중 펀딩 1~5000에 참여 이력이 없는 회원 1~5000이 보탠다 (개설자 본인은 피한다)
        const userId = (fundingId + randomInt(1, 4998)) % 5000 + 1;
        const res = http.post(`${BASE_URL}/api/fundings/share/${shareKeyOf(fundingId)}/contributions`, JSON.stringify({
            requestKey: `flow-${exec.vu.idInTest}-${exec.vu.iterationInScenario}-${Date.now()}`,
            amount: 10000, message: '생일 축하해!', paperTemplateCode: 'LIME_DOT_GRID',
        }), { ...params(userId, 'POST /api/fundings/share/{shareKey}/contributions'), responseCallback: http.expectedStatuses(201, 409) });
        check(res, { 'contribute 201/409': (r) => r.status === 201 || r.status === 409 });
    }
}

function homeUser() {
    const userId = randomInt(1, 10000);
    const requests = http.batch([
        ['GET', `${BASE_URL}/api/home/fundings`, null, params(userId, 'GET /api/home/fundings')],
        ['GET', `${BASE_URL}/api/home/surprise-reveals`, null, params(userId, 'GET /api/home/surprise-reveals')],
        ['GET', `${BASE_URL}/api/home/gift-verifications/banner`, null, params(userId, 'GET /api/home/gift-verifications/banner')],
    ]);
    check(requests, { 'home 200': (rs) => rs.every((r) => r.status === 200) });
    sleep(1);
    check(http.get(`${BASE_URL}/api/wishes`, params(userId, 'GET /api/wishes')), { 'wishes 200': (r) => r.status === 200 });
    sleep(1);
    check(http.get(`${BASE_URL}/api/recommended-products`, params(userId, 'GET /api/recommended-products')), { 'products 200': (r) => r.status === 200 });
    sleep(1);
    check(http.get(`${BASE_URL}/api/friends`, params(userId, 'GET /api/friends')), { 'friends 200': (r) => r.status === 200 });
}

function owner() {
    // 이미 확정한 보탬을 다시 확정하면 변경 없이 200이라 측정이 섞인다. 반복 번호(VU 전체에서 겹치지 않음)로 한 번씩만 쓰고,
    // 다 쓰면 조회만 하고 입금 확인은 건너뛴다
    const index = exec.scenario.iterationInTest;
    const [fundingId, contributionId] = pendingContributions[index % pendingContributions.length];
    const confirmable = index < pendingContributions.length;
    check(http.get(`${BASE_URL}/api/fundings/${fundingId}/management`, params(fundingId, 'GET /api/fundings/{fundingId}/management')),
        { 'management 200': (r) => r.status === 200 });
    sleep(1);
    check(http.get(`${BASE_URL}/api/fundings/${fundingId}/contributions/pending`, params(fundingId, 'GET /api/fundings/{fundingId}/contributions/pending')),
        { 'pending 200': (r) => r.status === 200 });
    if (!confirmable) {
        return;
    }
    sleep(1);
    // 같은 보탬을 두 번 확정하면 409라 실패로 세지 않는다
    const res = http.post(`${BASE_URL}/api/fundings/${fundingId}/contributions/${contributionId}/confirm`, null,
        { ...params(fundingId, 'POST /api/fundings/{fundingId}/contributions/{contributionId}/confirm'), responseCallback: http.expectedStatuses(200, 409) });
    check(res, { 'confirm 200/409': (r) => r.status === 200 || r.status === 409 });
}

export default function () {
    const roll = Math.random();
    if (roll < 0.55) {
        participant();
    } else if (roll < 0.85) {
        homeUser();
    } else {
        owner();
    }
    sleep(randomInt(5, 15) / 10);
}

// 동시 사용자 단계마다 처리량·평균·p95·p99·실패율을 한 줄씩 출력한다
export function handleSummary(data) {
    const rows = LEVELS.map((level) => {
        const duration = data.metrics[`http_req_duration{level:${level}}`]?.values || {};
        const failed = data.metrics[`http_req_failed{level:${level}}`]?.values || {};
        const count = data.metrics[`http_reqs{level:${level}}`]?.values?.count || 0;
        return `| ${level} | ${(count / (RAMP_SECONDS + STAGE_SECONDS)).toFixed(0)} | ${(duration.avg || 0).toFixed(1)} | ${(duration['p(95)'] || 0).toFixed(1)} | ${(duration['p(99)'] || 0).toFixed(1)} | ${((failed.rate || 0) * 100).toFixed(2)}% |`;
    });
    const table = ['| 동시 사용자 | req/s | 평균(ms) | p95(ms) | p99(ms) | 실패율 |', '|---|---|---|---|---|---|', ...rows].join('\n');
    return { stdout: `\n${table}\n`, [__ENV.SUMMARY_FILE || 'user-flow-summary.md']: table };
}
