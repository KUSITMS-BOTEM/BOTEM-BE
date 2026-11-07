package com.botem.domain.auth.service

import com.botem.domain.account.service.PayoutAccountService
import com.botem.domain.auth.client.KakaoOAuthClient
import com.botem.domain.auth.entity.RefreshToken
import com.botem.domain.auth.repository.RefreshTokenRepository
import com.botem.domain.user.repository.UserRepository
import com.botem.global.exception.AuthErrorCode
import com.botem.global.exception.BusinessException
import com.botem.global.jwt.JwtProvider
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.transaction.support.TransactionTemplate
import java.security.MessageDigest
import java.util.HexFormat

class AuthServiceLogoutTest {

    private val refreshTokenRepository = mock(RefreshTokenRepository::class.java)
    private val jwtProvider = mock(JwtProvider::class.java)
    private val authService = AuthService(
        mock(KakaoOAuthClient::class.java),
        mock(UserRepository::class.java),
        refreshTokenRepository,
        mock(PayoutAccountService::class.java),
        jwtProvider,
        mock(TransactionTemplate::class.java),
    )

    @Test
    fun `현재 Refresh 토큰이면 저장된 토큰을 삭제한다`() {
        val saved = RefreshToken(userId = 1L, tokenHash = sha256("current"))
        given(jwtProvider.getUserIdFromRefreshToken("current")).willReturn(1L)
        given(refreshTokenRepository.findByUserId(1L)).willReturn(saved)

        authService.logout("current")

        verify(refreshTokenRepository).delete(saved)
    }

    @Test
    fun `이미 교체된 Refresh 토큰이면 현재 세션의 토큰을 삭제하지 않는다`() {
        val saved = RefreshToken(userId = 1L, tokenHash = sha256("current"))
        given(jwtProvider.getUserIdFromRefreshToken("rotated")).willReturn(1L)
        given(refreshTokenRepository.findByUserId(1L)).willReturn(saved)

        authService.logout("rotated")

        verify(refreshTokenRepository, never()).delete(saved)
    }

    @Test
    fun `유효하지 않은 토큰이어도 예외 없이 종료한다`() {
        given(jwtProvider.getUserIdFromRefreshToken("invalid"))
            .willThrow(BusinessException(AuthErrorCode.INVALID_TOKEN))

        authService.logout("invalid")

        verifyNoInteractions(refreshTokenRepository)
    }

    private fun sha256(token: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.toByteArray()))
}
