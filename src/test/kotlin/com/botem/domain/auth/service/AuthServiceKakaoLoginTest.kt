package com.botem.domain.auth.service

import com.botem.domain.account.service.PayoutAccountService
import com.botem.domain.auth.client.KakaoOAuthClient
import com.botem.domain.auth.client.KakaoUserInfoResponse
import com.botem.domain.auth.entity.RefreshToken
import com.botem.domain.auth.repository.RefreshTokenRepository
import com.botem.domain.user.entity.User
import com.botem.domain.user.repository.UserRepository
import com.botem.global.exception.AuthErrorCode
import com.botem.global.exception.BusinessException
import com.botem.global.jwt.JwtProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.argThat
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime

class AuthServiceKakaoLoginTest {

    private val kakaoOAuthClient = mock(KakaoOAuthClient::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val refreshTokenRepository = mock(RefreshTokenRepository::class.java)
    private val payoutAccountService = mock(PayoutAccountService::class.java)
    private val jwtProvider = mock(JwtProvider::class.java)
    private val authService = AuthService(
        kakaoOAuthClient,
        userRepository,
        refreshTokenRepository,
        payoutAccountService,
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
    fun `받을 계좌를 등록한 기존 회원은 기존 회원으로 응답한다`() {
        given(userRepository.findByKakaoId(KAKAO_ID)).willReturn(user)
        given(payoutAccountService.hasPayoutAccount(anyLong())).willReturn(true)

        val result = authService.kakaoLogin("code", null)

        assertFalse(result.isNewUser)
        verify(userRepository, never()).save(any(User::class.java))
    }

    @Test
    fun `받을 계좌를 등록하지 않은 기존 회원은 신규 회원으로 응답한다`() {
        given(userRepository.findByKakaoId(KAKAO_ID)).willReturn(user)
        given(payoutAccountService.hasPayoutAccount(anyLong())).willReturn(false)

        val result = authService.kakaoLogin("code", null)

        assertTrue(result.isNewUser)
        verify(userRepository, never()).save(any(User::class.java))
    }

    @Test
    fun `탈퇴한 회원은 다시 로그인할 수 없다`() {
        user.withdraw(LocalDateTime.now())
        given(userRepository.findByKakaoId(KAKAO_ID)).willReturn(user)

        val exception = assertThrows(BusinessException::class.java) { authService.kakaoLogin("code", null) }

        assertEquals(AuthErrorCode.WITHDRAWN_USER, exception.errorCode)
        verify(jwtProvider, never()).createRefreshToken(anyLong())
    }

    @Test
    fun `카카오 닉네임 제공에 동의하지 않으면 가입하지 않는다`() {
        given(kakaoOAuthClient.getUserInfo("no-nickname", null)).willReturn(
            KakaoUserInfoResponse(id = KAKAO_ID, kakaoAccount = KakaoUserInfoResponse.KakaoAccount(profile = null)),
        )

        val exception = assertThrows(BusinessException::class.java) { authService.kakaoLogin("no-nickname", null) }

        assertEquals(AuthErrorCode.KAKAO_LOGIN_FAILED, exception.errorCode)
        verify(userRepository, never()).save(any(User::class.java))
    }

    @Test
    fun `카카오 로그인 URI는 카카오 클라이언트가 만든 값을 그대로 응답한다`() {
        given(kakaoOAuthClient.getLoginUri(null)).willReturn("https://kauth.kakao.com/oauth/authorize?client_id=x")

        assertEquals("https://kauth.kakao.com/oauth/authorize?client_id=x", authService.getKakaoLoginUri(null).loginUri)
    }

    @Test
    fun `카카오가 인증된 이메일을 주면 가입 시 저장하고 기존 회원은 갱신한다`() {
        givenKakaoEmail(email = "new@kakao.com", isEmailValid = true, isEmailVerified = true)
        given(userRepository.save(any(User::class.java))).willAnswer { it.arguments[0] }

        authService.kakaoLogin("code", null)

        verify(userRepository).save(argThat<User> { it.email == "new@kakao.com" })

        val existingUser = User(kakaoId = KAKAO_ID, nickname = "보탬", profileImageUrl = null, email = "old@kakao.com")
        given(userRepository.findByKakaoId(KAKAO_ID)).willReturn(existingUser)

        authService.kakaoLogin("code", null)

        assertEquals("new@kakao.com", existingUser.email)
    }

    @Test
    fun `카카오가 만료되었거나 인증되지 않은 이메일을 주면 저장된 이메일을 지운다`() {
        listOf(false to true, true to false, null to null).forEach { (isEmailValid, isEmailVerified) ->
            val existingUser = User(kakaoId = KAKAO_ID, nickname = "보탬", profileImageUrl = null, email = "old@kakao.com")
            given(userRepository.findByKakaoId(KAKAO_ID)).willReturn(existingUser)
            givenKakaoEmail(email = "old@kakao.com", isEmailValid = isEmailValid, isEmailVerified = isEmailVerified)

            authService.kakaoLogin("code", null)

            assertNull(existingUser.email)
        }
    }

    private fun givenKakaoEmail(email: String, isEmailValid: Boolean?, isEmailVerified: Boolean?) {
        given(kakaoOAuthClient.getUserInfo("code", null)).willReturn(
            KakaoUserInfoResponse(
                id = KAKAO_ID,
                kakaoAccount = KakaoUserInfoResponse.KakaoAccount(
                    profile = KakaoUserInfoResponse.Profile(nickname = "보탬", profileImageUrl = null),
                    email = email,
                    isEmailValid = isEmailValid,
                    isEmailVerified = isEmailVerified,
                ),
            ),
        )
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
