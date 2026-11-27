package com.botem.global.ratelimit

import com.botem.domain.user.entity.User
import com.botem.domain.user.entity.UserStatus
import com.botem.domain.user.repository.UserRepository
import com.botem.domain.user.service.UserStatusCache
import com.botem.domain.user.service.UserWithdrawalService
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import software.amazon.awssdk.services.s3.S3Client
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// 실제 Redis에서 요청 횟수 제한 카운터와 사용자 상태 캐시가 동작하는지 확인한다.
// Docker가 필요해 일반 test에서는 빠지고 `./gradlew redisTest`로 실행한다
@Tag("redis")
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = ["user-status-cache.enabled=true"])
class RedisIntegrationTest {

    @Autowired
    private lateinit var rateLimitCounter: RateLimitCounter

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userWithdrawalService: UserWithdrawalService

    @Autowired
    private lateinit var userStatusCache: UserStatusCache

    // 탈퇴 후 프로필 이미지 삭제가 실제 AWS를 부르지 않게 한다
    @MockitoBean
    private lateinit var s3Client: S3Client

    @Test
    fun `요청 횟수는 서버가 달라도 같은 키로 누적되고 키는 만료 시간을 가진다`() {
        val key = "rate-limit:TEST:1.1.1.1:1"

        assertEquals(1, rateLimitCounter.increment(key, Duration.ofMinutes(2)))
        assertEquals(2, rateLimitCounter.increment(key, Duration.ofMinutes(2)))
        assertTrue(redisTemplate.getExpire(key) in 1..120)
    }

    @Test
    fun `활성 회원 상태는 캐시하고 탈퇴하면 캐시 금지 표시로 덮어쓴다`() {
        val user = userRepository.save(User(kakaoId = 9_001L, nickname = "캐시 확인", profileImageUrl = null))

        assertEquals(UserStatus.ACTIVE, userStatusCache.getStatus(user.id))
        assertEquals("ACTIVE", redisTemplate.opsForValue().get("user-status:${user.id}"))

        userWithdrawalService.withdraw(user.id)

        assertEquals("BLOCKED", redisTemplate.opsForValue().get("user-status:${user.id}"))
        assertEquals(UserStatus.WITHDRAWN, userStatusCache.getStatus(user.id))
    }

    @Test
    fun `탈퇴 전에 읽은 활성 값은 캐시 금지 표시를 덮어쓰지 못한다`() {
        val user = userRepository.save(User(kakaoId = 9_002L, nickname = "경쟁 확인", profileImageUrl = null))
        // 탈퇴가 표시를 먼저 남긴 뒤, 탈퇴 전에 DB를 읽은 요청이 활성 값을 저장하려는 상황
        userStatusCache.blockCaching(user.id)

        assertEquals(UserStatus.ACTIVE, userStatusCache.getStatus(user.id))
        assertEquals("BLOCKED", redisTemplate.opsForValue().get("user-status:${user.id}"))
    }

    companion object {
        @Container
        @ServiceConnection(name = "redis")
        @JvmStatic
        val redis: GenericContainer<*> = GenericContainer("redis:7.4-alpine").withExposedPorts(6379)
    }
}
