package com.botem.domain.user.service

import com.botem.domain.user.entity.UserStatus
import com.botem.domain.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

// 인증된 요청마다 확인하는 회원 상태를 Redis에 캐시한다 (#282). 상태는 활성 → 탈퇴로만 바뀌므로 활성만 캐시한다.
// 탈퇴는 DB를 바꾸기 전에 '캐시 금지' 표시를 덮어쓰고, 활성 값은 키가 비어 있을 때만(SETNX) 저장한다.
// 그래서 탈퇴 전에 읽은 활성 값이 탈퇴 뒤에 다시 캐시되지 않는다
@Component
class UserStatusCache(
    private val redisTemplate: StringRedisTemplate,
    private val userRepository: UserRepository,
    private val clock: Clock,
    @Value("\${user-status-cache.enabled:true}") private val enabled: Boolean,
) {

    // Redis 장애 중에는 요청마다 실패하므로 경고는 1분에 한 번만 남긴다
    private val lastWarnedMinute = AtomicLong(-1)

    fun getStatus(userId: Long): UserStatus? {
        if (!enabled) {
            return userRepository.findStatusById(userId)
        }
        val key = keyOf(userId)
        val cached = try {
            redisTemplate.opsForValue().get(key)
        } catch (exception: RuntimeException) {
            warn(exception)
            // Redis를 쓸 수 없으면 DB만 본다
            return userRepository.findStatusById(userId)
        }
        if (cached == ACTIVE) {
            return UserStatus.ACTIVE
        }

        val status = userRepository.findStatusById(userId)
        if (status == UserStatus.ACTIVE && cached == null) {
            try {
                redisTemplate.opsForValue().setIfAbsent(key, ACTIVE, ACTIVE_TTL)
            } catch (exception: RuntimeException) {
                warn(exception)
            }
        }
        return status
    }

    // 탈퇴 트랜잭션 안에서 DB를 바꾸기 전에 호출한다. 표시를 쓰지 못하면 예외로 탈퇴를 실패시켜,
    // 탈퇴는 커밋됐는데 활성 캐시가 남는 경우를 만들지 않는다. 탈퇴가 롤백되면 표시 기간 동안 캐시만 쓰지 않는다
    fun blockCaching(userId: Long) {
        if (enabled) {
            redisTemplate.opsForValue().set(keyOf(userId), BLOCKED, BLOCKED_TTL)
        }
    }

    private fun warn(exception: RuntimeException) {
        val minute = clock.millis() / 60_000
        if (lastWarnedMinute.getAndSet(minute) != minute) {
            log.warn("회원 상태 캐시(Redis)를 쓸 수 없어 DB로 조회합니다: {}", exception.message)
        }
    }

    private fun keyOf(userId: Long): String = "$KEY_PREFIX:$userId"

    companion object {
        private val log = LoggerFactory.getLogger(UserStatusCache::class.java)
        private const val KEY_PREFIX = "user-status"
        private const val ACTIVE = "ACTIVE"
        private const val BLOCKED = "BLOCKED"
        private val ACTIVE_TTL: Duration = Duration.ofMinutes(5)
        // 탈퇴 직전에 읽고 아직 저장하지 못한 활성 값이 들어오지 못하도록 활성 캐시 TTL보다 길게 둔다
        private val BLOCKED_TTL: Duration = Duration.ofMinutes(10)
    }
}
