package com.botem.global.ratelimit

import com.botem.domain.user.entity.User
import com.botem.domain.user.entity.UserStatus
import com.botem.domain.user.repository.USER_STATUS_CACHE
import com.botem.domain.user.repository.UserRepository
import com.botem.domain.user.service.UserWithdrawalService
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.cache.CacheManager
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import software.amazon.awssdk.services.s3.S3Client
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// 실제 Redis에서 요청 횟수 제한 카운터와 사용자 상태 캐시가 동작하는지 확인한다.
// Docker가 필요해 일반 test에서는 빠지고 `./gradlew redisTest`로 실행한다
@Tag("redis")
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = ["spring.cache.type=redis"])
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
    private lateinit var cacheManager: CacheManager

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
    fun `활성 회원 상태는 캐시하고 탈퇴가 커밋되면 캐시를 지운다`() {
        val user = userRepository.save(User(kakaoId = 9_001L, nickname = "캐시 확인", profileImageUrl = null))
        val cache = assertNotNull(cacheManager.getCache(USER_STATUS_CACHE))

        assertEquals(UserStatus.ACTIVE, userRepository.findStatusById(user.id))
        assertEquals(UserStatus.ACTIVE, cache.get(user.id)?.get())

        userWithdrawalService.withdraw(user.id)

        assertNull(cache.get(user.id))
        assertEquals(UserStatus.WITHDRAWN, userRepository.findStatusById(user.id))
        assertNull(cache.get(user.id))
    }

    companion object {
        @Container
        @ServiceConnection(name = "redis")
        @JvmStatic
        val redis: GenericContainer<*> = GenericContainer("redis:7.4-alpine").withExposedPorts(6379)
    }
}
