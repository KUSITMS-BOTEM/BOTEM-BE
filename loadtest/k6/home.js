import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, accessTokenOf, randomInt } from './common.js';

// 로그인 홈의 내 펀딩 카드 조회. 펀딩을 가진 사용자 1~5000 중 무작위로 조회한다
export const options = {
    stages: [
        { duration: '10s', target: 50 },
        { duration: '40s', target: 50 },
        { duration: '10s', target: 0 },
    ],
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<300'],
    },
};

export function setup() {
    return { tokens: Array.from({ length: 500 }, (_, i) => accessTokenOf(i * 10 + 1)) };
}

export default function (data) {
    const token = data.tokens[randomInt(0, data.tokens.length - 1)];
    const res = http.get(`${BASE_URL}/api/home/fundings`, {
        headers: { Authorization: `Bearer ${token}` },
        tags: { name: 'GET /api/home/fundings' },
    });
    check(res, { 'status 200': (r) => r.status === 200 });
}
