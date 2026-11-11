package com.botem.domain.user.service

import com.botem.domain.account.repository.PayoutAccountRepository
import com.botem.domain.auth.repository.RefreshTokenRepository
import com.botem.domain.user.entity.User
import com.botem.domain.user.entity.UserStatus
import com.botem.domain.user.exception.UserErrorCode
import com.botem.domain.user.repository.UserRepository
import com.botem.global.exception.BusinessException
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDateTime
import java.time.MonthDay
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UserWithdrawalServiceTest {

    private val userRepository = mock(UserRepository::class.java)
    private val refreshTokenRepository = mock(RefreshTokenRepository::class.java)
    private val payoutAccountRepository = mock(PayoutAccountRepository::class.java)
    private val activeFundingChecker = mock(ActiveFundingChecker::class.java)
    private val eventPublisher = mock(ApplicationEventPublisher::class.java)

    @Suppress("UNCHECKED_CAST")
    private val activeFundingCheckerProvider =
        mock(ObjectProvider::class.java) as ObjectProvider<ActiveFundingChecker>

    private val service = UserWithdrawalService(
        userRepository = userRepository,
        refreshTokenRepository = refreshTokenRepository,
        payoutAccountRepository = payoutAccountRepository,
        activeFundingCheckerProvider = activeFundingCheckerProvider,
        eventPublisher = eventPublisher,
    )

    @Test
    fun `진행 중인 펀딩이 없으면 인증 정보와 계좌를 삭제하고 개인정보를 익명화한다`() {
        val user = user()
        `when`(userRepository.findByIdForUpdate(USER_ID)).thenReturn(user)
        `when`(activeFundingCheckerProvider.ifAvailable).thenReturn(activeFundingChecker)
        `when`(activeFundingChecker.existsByUserId(USER_ID)).thenReturn(false)

        service.withdraw(USER_ID)

        verify(refreshTokenRepository).deleteByUserId(USER_ID)
        verify(payoutAccountRepository).deleteAllByUserId(USER_ID)
        verify(eventPublisher).publishEvent(UserWithdrawnEvent(USER_ID))
        assertEquals(UserStatus.WITHDRAWN, user.status)
        assertEquals(-USER_ID, user.kakaoId)
        assertEquals("탈퇴한 사용자", user.nickname)
        assertNull(user.profileImageUrl)
        assertNull(user.birthday)
        assertNull(user.wishProfileKey)
        assertFalse(user.kakaoConnected)
        assertNotNull(user.withdrawnAt)
    }

    @Test
    fun `진행 중인 펀딩이 있으면 탈퇴할 수 없다`() {
        val user = user()
        `when`(userRepository.findByIdForUpdate(USER_ID)).thenReturn(user)
        `when`(activeFundingCheckerProvider.ifAvailable).thenReturn(activeFundingChecker)
        `when`(activeFundingChecker.existsByUserId(USER_ID)).thenReturn(true)

        val exception = assertFailsWith<BusinessException> {
            service.withdraw(USER_ID)
        }

        assertEquals(UserErrorCode.ACTIVE_FUNDING_EXISTS, exception.errorCode)
        assertEquals(UserStatus.ACTIVE, user.status)
        verifyNoInteractions(refreshTokenRepository, payoutAccountRepository)
    }

    @Test
    fun `펀딩 확인 기능이 연결되지 않으면 탈퇴를 진행하지 않는다`() {
        val user = user()
        `when`(userRepository.findByIdForUpdate(USER_ID)).thenReturn(user)

        val exception = assertFailsWith<BusinessException> {
            service.withdraw(USER_ID)
        }

        assertEquals(UserErrorCode.WITHDRAWAL_CHECK_UNAVAILABLE, exception.errorCode)
        assertEquals(UserStatus.ACTIVE, user.status)
        verifyNoInteractions(refreshTokenRepository, payoutAccountRepository)
    }

    @Test
    fun `이미 탈퇴한 사용자의 반복 요청은 추가 처리 없이 성공한다`() {
        val user = user().apply { withdraw(LocalDateTime.now()) }
        `when`(userRepository.findByIdForUpdate(USER_ID)).thenReturn(user)

        service.withdraw(USER_ID)

        verifyNoInteractions(activeFundingCheckerProvider, refreshTokenRepository, payoutAccountRepository)
    }

    @Test
    fun `존재하지 않는 사용자는 탈퇴할 수 없다`() {
        val exception = assertFailsWith<BusinessException> {
            service.withdraw(USER_ID)
        }

        assertEquals(UserErrorCode.USER_NOT_FOUND, exception.errorCode)
        verifyNoInteractions(activeFundingCheckerProvider, refreshTokenRepository, payoutAccountRepository)
    }

    private fun user(): User =
        User(
            kakaoId = 1234L,
            nickname = "지유",
            profileImageUrl = "https://example.com/profile.jpg",
            birthday = MonthDay.of(10, 13),
            wishProfileKey = "8f0c6d779fae47f3a979302f2782d20f",
        ).also { user ->
            User::class.java.getDeclaredField("id").apply {
                isAccessible = true
                set(user, USER_ID)
            }
        }

    companion object {
        private const val USER_ID = 1L
    }
}
