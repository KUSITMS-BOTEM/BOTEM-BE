import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, shareKeyOf } from './common.js';

// 같은 펀딩(5001번)에 동시에 보태기. 펀딩 행 비관적 락으로 직렬화되는지 확인한다
// - distinct_guests: 서로 다른 비회원 200명이 동시에 보탠다 → 200건 모두 성공해야 한다
// - same_name_guests: 같은 이름 50건이 동시에 들어온다 → 1건만 성공하고 나머지는 409(FUNDING_032)여야 한다
const FUNDING_ID = Number(__ENV.FUNDING_ID || 5001);
const created = new Counter('contribution_created');
const duplicatedName = new Counter('contribution_duplicated_name');

export const options = {
    scenarios: {
        distinct_guests: {
            executor: 'shared-iterations',
            vus: 100,
            iterations: 200,
            exec: 'distinctGuest',
        },
        same_name_guests: {
            executor: 'shared-iterations',
            vus: 50,
            iterations: 50,
            exec: 'sameNameGuest',
            startTime: '15s',
        },
    },
    thresholds: {
        'contribution_created{scenario:distinct_guests}': ['count==200'],
        'contribution_created{scenario:same_name_guests}': ['count==1'],
        'contribution_duplicated_name{scenario:same_name_guests}': ['count==49'],
    },
};

// 실행마다 이름과 requestKey가 겹치지 않도록 한 번만 만든 값을 모든 VU가 공유한다
export function setup() {
    return { runId: __ENV.RUN_ID || Date.now().toString(36) };
}

function contribute(participantName, requestKey) {
    const res = http.post(
        `${BASE_URL}/api/fundings/share/${shareKeyOf(FUNDING_ID)}/contributions`,
        JSON.stringify({
            requestKey,
            participantName,
            amount: 10000,
            message: '생일 축하해!',
            paperTemplateCode: 'LIME_DOT_GRID',
            guestPassword: '1234',
        }),
        { headers: { 'Content-Type': 'application/json' }, tags: { name: 'POST /api/fundings/share/{shareKey}/contributions' } },
    );
    if (res.status === 201) {
        created.add(1);
    } else if (res.status === 409 && res.json('error.code') === 'FUNDING_032') {
        duplicatedName.add(1);
    }
    return res;
}

export function distinctGuest(data) {
    const res = contribute(`게스트-${data.runId}-${__VU}-${__ITER}`, `${data.runId}-d-${__VU}-${__ITER}`);
    check(res, { 'status 201': (r) => r.status === 201 });
}

export function sameNameGuest(data) {
    const res = contribute(`동명이인-${data.runId}`, `${data.runId}-s-${__VU}-${__ITER}`);
    check(res, { 'status 201 or 409': (r) => r.status === 201 || r.status === 409 });
}
