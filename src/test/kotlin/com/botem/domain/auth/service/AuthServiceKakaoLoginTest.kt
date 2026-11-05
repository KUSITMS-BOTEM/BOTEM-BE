package com.botem.domain.auth.service

import com.botem.domain.auth.client.KakaoOAuthClient
import com.botem.domain.auth.client.KakaoUserInfoResponse
import com.botem.domain.auth.entity.RefreshToken
import com.botem.domain.auth.repository.RefreshTokenRepository
import com.botem.domain.user.entity.User
import com.botem.domain.user.repository.UserRepository
import com.botem.global.jwt.JwtProvider
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate

class AuthServiceKakaoLoginTest {

    private val kakaoOAuthClient = mock(KakaoOAuthClient::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val refreshTokenRepository = mock(RefreshTokenRepository::class.java)
    private val jwtProvider = mock(JwtProvider::class.java)
    private val authService = AuthService(
        kakaoOAuthClient,
        userRepository,
        refreshTokenRepository,
        jwtProvider,
        TransactionTemplate(NoOpTransactionManager),
    )

    private val user = User(kakaoId = KAKAO_ID, nickname = "보탬", profileImageUrl = null)

    @BeforeEach
    fun setUp() {
        given(kakaoOAuthClient.getUserInfo("code", null)).willReturn(
            KakaoUserInfoResponse(
                id = KAKAO_ID,
                kakaoAccount = KakaoUserInfoResponse.KakaoAccount(
                    KakaoUserInfoResponse.Profile(nickname = "보탬", profileImageUrl = null),
                ),
            ),
        )
        given(jwtProvider.createAccessToken(anyLong())).willReturn("access")
        given(jwtProvider.createRefreshToken(anyLong())).willReturn("refresh")
        given(refreshTokenRepository.save(any(RefreshToken::class.java))).willAnswer { it.arguments[0] }
    }

    @Test
    fun `처음 로그인한 회원은 가입 후 신규 회원으로 응답한다`() {
        given(userRepository.save(any(User::class.java))).willReturn(user)

        val result = authService.kakaoLogin("code", null)

        assertTrue(result.isNewUser)
    }

    @Test
    fun `이미 가입한 회원은 기존 회원으로 응답한다`() {
        given(userRepository.findByKakaoId(KAKAO_ID)).willReturn(user)

        val result = authService.kakaoLogin("code", null)

        assertFalse(result.isNewUser)
        verify(userRepository, never()).save(any(User::class.java))
    }

    private object NoOpTransactionManager : PlatformTransactionManager {
        override fun getTransaction(definition: TransactionDefinition?): TransactionStatus = SimpleTransactionStatus()

        override fun commit(status: TransactionStatus) = Unit

        override fun rollback(status: TransactionStatus) = Unit
    }

    companion object {
        private const val KAKAO_ID = 1L
    }
}
