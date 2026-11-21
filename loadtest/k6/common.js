import crypto from 'k6/crypto';
import encoding from 'k6/encoding';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// seed.sql과 같은 규칙: 펀딩 ID를 32자리 16진수로 채운 값
export function shareKeyOf(fundingId) {
    return fundingId.toString(16).padStart(32, '0');
}

export function randomInt(min, max) {
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

// JwtProvider와 같은 형식(HS256, sub=userId, type=access)의 액세스 토큰. 키는 JWT_SECRET 환경 변수로 받는다
export function accessTokenOf(userId) {
    const secret = __ENV.JWT_SECRET;
    if (!secret) {
        throw new Error('JWT_SECRET 환경 변수가 필요합니다.');
    }
    const now = Math.floor(Date.now() / 1000);
    const header = encoding.b64encode(JSON.stringify({ alg: 'HS256', typ: 'JWT' }), 'rawurl');
    const payload = encoding.b64encode(
        JSON.stringify({ jti: `${userId}-${now}`, sub: String(userId), type: 'access', iat: now, exp: now + 3600 }),
        'rawurl',
    );
    const signature = crypto.hmac('sha256', encoding.b64decode(secret, 'std'), `${header}.${payload}`, 'base64rawurl');
    return `${header}.${payload}.${signature}`;
}
