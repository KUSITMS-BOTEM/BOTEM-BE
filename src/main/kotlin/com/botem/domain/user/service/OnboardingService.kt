package com.botem.domain.user.service

import com.botem.domain.account.dto.request.SavePayoutAccountRequest
import com.botem.domain.account.service.PayoutAccountService
import com.botem.domain.user.dto.request.RegisterOnboardingRequest
import com.botem.domain.user.exception.UserErrorCode
import com.botem.global.exception.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OnboardingService(
    private val userService: UserService,
    private val payoutAccountService: PayoutAccountService,
) {

    // 프로필 저장 시 회원 행을 잠가 동시 요청을 직렬화하고, 계좌 등록에 실패하면 프로필 저장도 함께 롤백
    @Transactional
    fun completeOnboarding(userId: Long, request: RegisterOnboardingRequest) {
        userService.updateOnboardingProfile(userId, request.name, request.birthday)

        if (payoutAccountService.hasPayoutAccount(userId)) {
            throw BusinessException(UserErrorCode.ONBOARDING_ALREADY_COMPLETED)
        }

        payoutAccountService.createPayoutAccount(
            userId,
            SavePayoutAccountRequest(bank = request.bank, accountNumber = request.accountNumber),
        )
    }
}
