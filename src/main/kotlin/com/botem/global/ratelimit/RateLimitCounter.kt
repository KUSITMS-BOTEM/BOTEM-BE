package com.botem.global.ratelimit

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import java.time.Duration

// 구간(window)별 요청 수를 세는 저장소. 서버 여러 대가 같은 값을 보도록 운영은 Redis를 쓴다
fun interface RateLimitCounter {
    fun increment(key: String, expiresIn: Duration): Long
}

class RedisRateLimitCounter(
    private val redisTemplate: StringRedisTemplate,
) : RateLimitCounter {

    // 키에 구간 번호가 들어 있어 구간이 바뀌면 새 키로 센다. 증가와 첫 만료 설정을 한 스크립트로 실행해,
    // 증가 직후 연결이 끊겨도 만료 없는 키가 남지 않게 한다
    override fun increment(key: String, expiresIn: Duration): Long =
        checkNotNull(redisTemplate.execute(INCREMENT_WITH_EXPIRE, listOf(key), expiresIn.toMillis().toString()))

    companion object {
        private val INCREMENT_WITH_EXPIRE: RedisScript<Long> = RedisScript.of(
            """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """.trimIndent(),
            Long::class.java,
        )
    }
}
