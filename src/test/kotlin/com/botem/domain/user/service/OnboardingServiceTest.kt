package com.botem.domain.user.service

import com.botem.domain.account.dto.request.SavePayoutAccountRequest
import com.botem.domain.account.entity.Bank
import com.botem.domain.account.service.PayoutAccountService
import com.botem.domain.user.dto.request.RegisterOnboardingRequest
import com.botem.domain.user.exception.UserErrorCode
import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import java.time.MonthDay
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OnboardingServiceTest {

    private val userService = mock(UserService::class.java)
    private val payoutAccountService = mock(PayoutAccountService::class.java)
    private val profileImageService = mock(ProfileImageService::class.java)
    private val onboardingService = OnboardingService(userService, payoutAccountService, profileImageService)

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

        verify(userService).updateOnboardingProfile(USER_ID, "이정민", MonthDay.of(10, 13), null)
        verify(payoutAccountService).createPayoutAccount(
            USER_ID,
            SavePayoutAccountRequest(bank = Bank.TOSS_BANK, accountNumber = "100159014821"),
        )
    }

    @Test
    fun `프로필 이미지 URL이 허용되지 않으면 아무것도 저장하지 않는다`() {
        val imageUrl = "https://evil.example.com/a.jpg"
        given(profileImageService.validateImageUrl(USER_ID, imageUrl))
            .willThrow(BusinessException(CommonErrorCode.INVALID_INPUT_VALUE))

        assertFailsWith<BusinessException> {
            onboardingService.completeOnboarding(USER_ID, request.copy(profileImageUrl = imageUrl))
        }

        verifyNoInteractions(userService, payoutAccountService)
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
