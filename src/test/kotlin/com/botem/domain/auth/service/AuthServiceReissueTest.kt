package com.botem.domain.auth.service

import com.botem.domain.account.service.PayoutAccountService
import com.botem.domain.auth.client.KakaoOAuthClient
import com.botem.domain.auth.entity.RefreshToken
import com.botem.domain.auth.repository.RefreshTokenRepository
import com.botem.domain.user.entity.UserStatus
import com.botem.domain.user.repository.UserRepository
import com.botem.global.exception.AuthErrorCode
import com.botem.global.exception.BusinessException
import com.botem.global.jwt.JwtProvider
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.transaction.support.TransactionTemplate
import java.security.MessageDigest
import java.util.HexFormat
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AuthServiceReissueTest {

    private val userRepository = mock(UserRepository::class.java)
    private val refreshTokenRepository = mock(RefreshTokenRepository::class.java)
    private val jwtProvider = mock(JwtProvider::class.java)
    private val authService = AuthService(
        mock(KakaoOAuthClient::class.java),
        userRepository,
        refreshTokenRepository,
        mock(PayoutAccountService::class.java),
        jwtProvider,
        mock(TransactionTemplate::class.java),
    )

    @BeforeEach
    fun setUp() {
        given(jwtProvider.getUserIdFromRefreshToken(CURRENT_TOKEN)).willReturn(USER_ID)
        given(jwtProvider.createAccessToken(USER_ID)).willReturn("new-access")
        given(jwtProvider.createRefreshToken(USER_ID)).willReturn("new-refresh")
    }

    @Test
    fun `저장된 현재 Refresh 토큰이면 새 토큰을 발급하고 저장된 토큰을 교체한다`() {
        val saved = RefreshToken(userId = USER_ID, tokenHash = sha256(CURRENT_TOKEN))
        given(userRepository.findStatusById(USER_ID)).willReturn(UserStatus.ACTIVE)
        given(refreshTokenRepository.findByUserId(USER_ID)).willReturn(saved)

        val tokens = authService.reissue(CURRENT_TOKEN)

        assertEquals("new-access", tokens.accessToken)
        assertEquals("new-refresh", tokens.refreshToken)
        assertEquals(sha256("new-refresh"), saved.tokenHash)
    }

    @Test
    fun `이미 교체된 Refresh 토큰을 다시 쓰면 재발급하지 않는다`() {
        given(userRepository.findStatusById(USER_ID)).willReturn(UserStatus.ACTIVE)
        given(refreshTokenRepository.findByUserId(USER_ID))
            .willReturn(RefreshToken(userId = USER_ID, tokenHash = sha256("rotated-token")))

        assertReissueFails(AuthErrorCode.INVALID_TOKEN)
    }

    @Test
    fun `저장된 Refresh 토큰이 없으면 재발급하지 않는다`() {
        given(userRepository.findStatusById(USER_ID)).willReturn(UserStatus.ACTIVE)

        assertReissueFails(AuthErrorCode.INVALID_TOKEN)
    }

    @Test
    fun `탈퇴한 회원은 재발급할 수 없다`() {
        given(userRepository.findStatusById(USER_ID)).willReturn(UserStatus.WITHDRAWN)

        assertReissueFails(AuthErrorCode.WITHDRAWN_USER)
    }

    @Test
    fun `존재하지 않는 회원의 토큰이면 재발급하지 않는다`() {
        assertReissueFails(AuthErrorCode.INVALID_TOKEN)
    }

    private fun assertReissueFails(errorCode: AuthErrorCode) {
        val exception = assertFailsWith<BusinessException> { authService.reissue(CURRENT_TOKEN) }

        assertEquals(errorCode, exception.errorCode)
        verify(jwtProvider, never()).createRefreshToken(USER_ID)
    }

    private fun sha256(token: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.toByteArray()))

    companion object {
        private const val USER_ID = 1L
        private const val CURRENT_TOKEN = "current-refresh"
    }
}
