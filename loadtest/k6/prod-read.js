import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';

// 운영 EC2 조회 전용 단계 부하 (#273). 초당 요청 수를 단계적으로 올려 p95가 300ms를 넘는 지점을 찾는다.
// 운영 DB에 아무것도 쓰지 않도록 GET만 보낸다. 응답이 크게 느려지거나 실패가 늘면 바로 멈춘다.
//
// BASE_URL=https://<운영 도메인> SHARE_KEYS=<공유키1>,<공유키2> WISH_PROFILE_KEYS=<위시 프로필 키> \
// ACCESS_TOKEN=<내 액세스 토큰(선택)> k6 run prod-read.js
//
// 공유 키·위시 프로필 키·토큰은 셸 환경 변수로만 넘기고 파일이나 대화에 남기지 않는다
const BASE_URL = __ENV.BASE_URL;
const SHARE_KEYS = (__ENV.SHARE_KEYS || '').split(',').filter(Boolean);
const WISH_PROFILE_KEYS = (__ENV.WISH_PROFILE_KEYS || '').split(',').filter(Boolean);
const ACCESS_TOKEN = __ENV.ACCESS_TOKEN;
const RATES = (__ENV.RATES || '5,10,20,40,80').split(',').map(Number);
const STAGE_SECONDS = Number(__ENV.STAGE_SECONDS || 60);
const RAMP_SECONDS = 10;

if (!BASE_URL || SHARE_KEYS.length === 0) {
    throw new Error('BASE_URL과 SHARE_KEYS 환경 변수가 필요합니다.');
}

const requests = [
    (n) => ['GET /api/fundings/share/{shareKey}', `/api/fundings/share/${SHARE_KEYS[n % SHARE_KEYS.length]}`, false],
    ...(WISH_PROFILE_KEYS.length > 0
        ? [(n) => ['GET /api/wish-profiles/{wishProfileKey}', `/api/wish-profiles/${WISH_PROFILE_KEYS[n % WISH_PROFILE_KEYS.length]}`, false]]
        : []),
    ...(ACCESS_TOKEN
        ? [
            () => ['GET /api/home/fundings', '/api/home/fundings', true],
            () => ['GET /api/recommended-products', '/api/recommended-products', true],
            () => ['GET /api/wishes', '/api/wishes', true],
        ]
        : []),
];

function currentRate() {
    const elapsed = exec.instance.currentTestRunDuration / 1000;
    const index = Math.min(Math.floor(elapsed / (RAMP_SECONDS + STAGE_SECONDS)), RATES.length - 1);
    return String(RATES[index]);
}

export const options = {
    scenarios: {
        read: {
            executor: 'ramping-arrival-rate',
            startRate: 1,
            timeUnit: '1s',
            preAllocatedVUs: 20,
            maxVUs: 200,
            stages: RATES.flatMap((target) => [
                { duration: `${RAMP_SECONDS}s`, target },
                { duration: `${STAGE_SECONDS}s`, target },
            ]),
        },
    },
    thresholds: {
        // 운영 사용자에게 영향이 가기 전에 멈춘다
        http_req_duration: [{ threshold: 'p(95)<1000', abortOnFail: true, delayAbortEval: '20s' }],
        http_req_failed: [{ threshold: 'rate<0.05', abortOnFail: true, delayAbortEval: '20s' }],
        ...Object.fromEntries(RATES.flatMap((rate) => [
            [`http_req_duration{rate:${rate}}`, ['p(95)<300']],
            // 앞 단계의 성공 요청이 현재 단계의 장애를 가리지 않도록 단계별 실패율로도 멈춘다
            [`http_req_failed{rate:${rate}}`, ['rate<0.01', { threshold: 'rate<0.05', abortOnFail: true, delayAbortEval: '10s' }]],
            [`http_reqs{rate:${rate}}`, ['count>0']],
        ])),
    },
    summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

export default function () {
    const n = exec.scenario.iterationInTest;
    // 요청 종류마다 호출 횟수로 키를 돌려야 종류 수와 키 수가 맞물려도 모든 키를 고르게 조회한다
    const [name, path, authenticated] = requests[n % requests.length](Math.floor(n / requests.length));
    const headers = authenticated ? { Authorization: `Bearer ${ACCESS_TOKEN}` } : {};
    const res = http.get(`${BASE_URL}${path}`, { headers, tags: { name, rate: currentRate() } });
    check(res, { 'status 200': (r) => r.status === 200 });
}

export function handleSummary(data) {
    const rows = RATES.map((rate) => {
        const duration = data.metrics[`http_req_duration{rate:${rate}}`]?.values || {};
        const failed = data.metrics[`http_req_failed{rate:${rate}}`]?.values || {};
        const count = data.metrics[`http_reqs{rate:${rate}}`]?.values?.count || 0;
        return `| ${rate} | ${count} | ${(duration.avg || 0).toFixed(1)} | ${(duration['p(95)'] || 0).toFixed(1)} | ${(duration['p(99)'] || 0).toFixed(1)} | ${((failed.rate || 0) * 100).toFixed(2)}% |`;
    });
    const table = ['| 목표 req/s | 요청 수 | 평균(ms) | p95(ms) | p99(ms) | 실패율 |', '|---|---|---|---|---|---|', ...rows].join('\n');
    return { stdout: `\n${table}\n`, [__ENV.SUMMARY_FILE || 'prod-read-summary.md']: table };
}
