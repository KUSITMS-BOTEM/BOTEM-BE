package com.botem.domain.user.service

import com.botem.domain.account.service.PayoutAccountService
import com.botem.domain.auth.service.RefreshTokenService
import com.botem.domain.user.entity.UserStatus
import com.botem.domain.user.exception.UserErrorCode
import com.botem.domain.user.repository.UserRepository
import com.botem.global.exception.BusinessException
import java.time.LocalDateTime
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserWithdrawalService(
    private val userRepository: UserRepository,
    private val refreshTokenService: RefreshTokenService,
    private val payoutAccountService: PayoutAccountService,
    private val activeFundingChecker: ActiveFundingChecker,
    private val eventPublisher: ApplicationEventPublisher,
) {

    @Transactional
    fun withdraw(userId: Long) {
        val user = userRepository.findByIdForUpdate(userId)
            ?: throw BusinessException(UserErrorCode.USER_NOT_FOUND)

        if (user.status == UserStatus.WITHDRAWN) {
            return
        }

        if (activeFundingChecker.existsByUserId(userId)) {
            throw BusinessException(UserErrorCode.ACTIVE_FUNDING_EXISTS)
        }

        refreshTokenService.deleteAllOf(userId)
        payoutAccountService.deleteAllOf(userId)
        user.withdraw(LocalDateTime.now())
        eventPublisher.publishEvent(UserWithdrawnEvent(userId))
    }
}
