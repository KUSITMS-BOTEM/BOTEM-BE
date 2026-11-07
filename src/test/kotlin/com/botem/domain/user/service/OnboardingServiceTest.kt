package com.botem.domain.user.service

import com.botem.domain.account.dto.request.SavePayoutAccountRequest
import com.botem.domain.account.entity.Bank
import com.botem.domain.account.service.PayoutAccountService
import com.botem.domain.user.dto.request.RegisterOnboardingRequest
import com.botem.domain.user.exception.UserErrorCode
import com.botem.global.exception.BusinessException
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import java.time.MonthDay
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OnboardingServiceTest {

    private val userService = mock(UserService::class.java)
    private val payoutAccountService = mock(PayoutAccountService::class.java)
    private val onboardingService = OnboardingService(userService, payoutAccountService)

    private val request = RegisterOnboardingRequest(
        name = "이정민",
        birthday = MonthDay.of(10, 13),
        bank = Bank.TOSS_BANK,
        accountNumber = "100159014821",
    )

    @Test
    fun `이름·생일을 저장하고 받을 계좌를 등록한다`() {
        given(payoutAccountService.hasPayoutAccount(USER_ID)).willReturn(false)

        onboardingService.completeOnboarding(USER_ID, request)

        verify(userService).updateOnboardingProfile(USER_ID, "이정민", MonthDay.of(10, 13))
        verify(payoutAccountService).createPayoutAccount(
            USER_ID,
            SavePayoutAccountRequest(bank = Bank.TOSS_BANK, accountNumber = "100159014821"),
        )
    }

    @Test
    fun `받을 계좌가 이미 있으면 온보딩을 다시 진행할 수 없다`() {
        given(payoutAccountService.hasPayoutAccount(USER_ID)).willReturn(true)

        val exception = assertFailsWith<BusinessException> {
            onboardingService.completeOnboarding(USER_ID, request)
        }

        assertEquals(UserErrorCode.ONBOARDING_ALREADY_COMPLETED, exception.errorCode)
        verify(payoutAccountService).hasPayoutAccount(USER_ID)
        verifyNoMoreInteractions(payoutAccountService)
    }

    companion object {
        private const val USER_ID = 1L
    }
}
