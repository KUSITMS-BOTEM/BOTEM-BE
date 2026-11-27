package com.botem.global.ratelimit

import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration

// 구간(window)별 요청 수를 세는 저장소. 서버 여러 대가 같은 값을 보도록 운영은 Redis를 쓴다
fun interface RateLimitCounter {
    fun increment(key: String, expiresIn: Duration): Long
}

class RedisRateLimitCounter(
    private val redisTemplate: StringRedisTemplate,
) : RateLimitCounter {

    // 키에 구간 번호가 들어 있어 구간이 바뀌면 새 키로 센다. 만료는 첫 요청에만 걸어 지난 구간 키가 쌓이지 않게 한다
    override fun increment(key: String, expiresIn: Duration): Long {
        val count = checkNotNull(redisTemplate.opsForValue().increment(key))
        if (count == 1L) {
            redisTemplate.expire(key, expiresIn)
        }
        return count
    }
}
