package com.botem.global.security

import com.botem.global.config.JwtProperties
import com.botem.global.exception.AuthErrorCode
import com.botem.global.exception.BusinessException
import com.botem.global.jwt.JwtProvider
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class JwtProviderTest {

    private val jwtProvider = createJwtProvider(Duration.ofMinutes(60))

    @Test
    fun `Access 토큰에서 사용자 ID를 꺼낸다`() {
        val token = jwtProvider.createAccessToken(1L)

        assertEquals(1L, jwtProvider.getUserIdFromAccessToken(token))
    }

    @Test
    fun `Refresh 토큰은 Access 토큰으로 사용할 수 없다`() {
        val refreshToken = jwtProvider.createRefreshToken(1L)

        val exception = assertFailsWith<BusinessException> { jwtProvider.getUserIdFromAccessToken(refreshToken) }
        assertEquals(AuthErrorCode.INVALID_TOKEN, exception.errorCode)
    }

    @Test
    fun `같은 사용자에게 연속으로 발급한 Refresh 토큰은 서로 다르다`() {
        val firstToken = jwtProvider.createRefreshToken(1L)
        val secondToken = jwtProvider.createRefreshToken(1L)

        assertNotEquals(firstToken, secondToken)
    }

    @Test
    fun `다른 키로 서명된 토큰은 거부한다`() {
        val otherProvider = createJwtProvider(Duration.ofMinutes(60), OTHER_SECRET)
        val token = otherProvider.createAccessToken(1L)

        val exception = assertFailsWith<BusinessException> { jwtProvider.getUserIdFromAccessToken(token) }
        assertEquals(AuthErrorCode.INVALID_TOKEN, exception.errorCode)
    }

    @Test
    fun `만료된 토큰은 EXPIRED_TOKEN으로 거부한다`() {
        val expiredProvider = createJwtProvider(Duration.ofMinutes(-1))
        val token = expiredProvider.createAccessToken(1L)

        val exception = assertFailsWith<BusinessException> { jwtProvider.getUserIdFromAccessToken(token) }
        assertEquals(AuthErrorCode.EXPIRED_TOKEN, exception.errorCode)
    }

    private fun createJwtProvider(expiration: Duration, secret: String = SECRET) =
        JwtProvider(
            JwtProperties(
                secret = secret,
                accessTokenExpiration = expiration,
                refreshTokenExpiration = expiration
            )
        )

    companion object {
        private const val SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtcHJvdmlkZXItdGVzdC0xMjM0NTY3ODkw"
        private const val OTHER_SECRET = "b3RoZXItc2VjcmV0LWtleS1mb3Itand0LXByb3ZpZGVyLXRlc3QtMTIzNDU2Nzg5MA=="
    }
}
