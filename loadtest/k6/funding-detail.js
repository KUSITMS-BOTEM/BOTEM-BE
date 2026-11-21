import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, randomInt, shareKeyOf } from './common.js';

// 비로그인 공유 화면의 펀딩 상세 조회. 보탬 50건이 있는 펀딩 5,000개 중 무작위로 조회한다
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

export default function () {
    const res = http.get(`${BASE_URL}/api/fundings/share/${shareKeyOf(randomInt(1, 5000))}`, {
        tags: { name: 'GET /api/fundings/share/{shareKey}' },
    });
    check(res, { 'status 200': (r) => r.status === 200 });
}
