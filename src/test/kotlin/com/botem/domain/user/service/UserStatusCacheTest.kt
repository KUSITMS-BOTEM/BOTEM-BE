package com.botem.domain.user.service

import com.botem.domain.user.entity.UserStatus
import com.botem.domain.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UserStatusCacheTest {

    private val redisTemplate = mock(StringRedisTemplate::class.java)
    private val userRepository = mock(UserRepository::class.java)

    @Test
    fun `Redis를 쓸 수 없으면 DB의 회원 상태로 인증한다`() {
        `when`(redisTemplate.opsForValue()).thenThrow(RedisConnectionFailureException("연결 실패"))
        `when`(userRepository.findStatusById(USER_ID)).thenReturn(UserStatus.WITHDRAWN)

        assertEquals(UserStatus.WITHDRAWN, cache(enabled = true).getStatus(USER_ID))
    }

    @Test
    fun `캐시 금지 표시를 쓰지 못하면 예외로 탈퇴를 실패시킨다`() {
        `when`(redisTemplate.opsForValue()).thenThrow(RedisConnectionFailureException("연결 실패"))

        assertFailsWith<RedisConnectionFailureException> { cache(enabled = true).blockCaching(USER_ID) }
    }

    @Test
    fun `캐시를 끄면 Redis를 쓰지 않는다`() {
        `when`(userRepository.findStatusById(USER_ID)).thenReturn(UserStatus.ACTIVE)
        val cache = cache(enabled = false)

        assertEquals(UserStatus.ACTIVE, cache.getStatus(USER_ID))
        cache.blockCaching(USER_ID)
        verifyNoInteractions(redisTemplate)
    }

    private fun cache(enabled: Boolean) = UserStatusCache(redisTemplate, userRepository, Clock.systemUTC(), enabled)

    companion object {
        private const val USER_ID = 7L
    }
}
