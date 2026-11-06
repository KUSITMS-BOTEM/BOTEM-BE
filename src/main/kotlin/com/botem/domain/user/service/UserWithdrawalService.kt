package com.botem.domain.user.service

import com.botem.domain.account.repository.PayoutAccountRepository
import com.botem.domain.auth.repository.RefreshTokenRepository
import com.botem.domain.user.entity.UserStatus
import com.botem.domain.user.exception.UserErrorCode
import com.botem.domain.user.repository.UserRepository
import com.botem.global.exception.BusinessException
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class UserWithdrawalService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val payoutAccountRepository: PayoutAccountRepository,
    private val activeFundingCheckerProvider: ObjectProvider<ActiveFundingChecker>,
) {

    @Transactional
    fun withdraw(userId: Long) {
        val user = userRepository.findByIdForUpdate(userId)
            ?: throw BusinessException(UserErrorCode.USER_NOT_FOUND)

        if (user.status == UserStatus.WITHDRAWN) {
            return
        }

        val activeFundingChecker = activeFundingCheckerProvider.ifAvailable
            ?: throw BusinessException(UserErrorCode.WITHDRAWAL_CHECK_UNAVAILABLE)

        if (activeFundingChecker.existsByUserId(userId)) {
            throw BusinessException(UserErrorCode.ACTIVE_FUNDING_EXISTS)
        }

        refreshTokenRepository.deleteByUserId(userId)
        payoutAccountRepository.deleteAllByUserId(userId)
        user.withdraw(LocalDateTime.now())
    }
}
