package com.botem.global.ratelimit

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

// Redis 없이 요청 횟수 제한 동작을 검증하기 위한 테스트용 카운터
class InMemoryRateLimitCounter : RateLimitCounter {

    private val counts = ConcurrentHashMap<String, AtomicLong>()

    override fun increment(key: String, expiresIn: Duration): Long =
        counts.computeIfAbsent(key) { AtomicLong() }.incrementAndGet()
}
