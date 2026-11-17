package com.botem.domain.funding.service

import com.botem.domain.account.entity.AccountVerificationStatus
import com.botem.domain.account.entity.PayoutAccount
import com.botem.domain.account.exception.PayoutAccountErrorCode
import com.botem.domain.account.repository.PayoutAccountRepository
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.wish.entity.Wish
import com.botem.domain.wish.exception.WishErrorCode
import com.botem.domain.wish.repository.WishRepository
import com.botem.global.exception.BusinessException
import org.springframework.stereotype.Component

@Component
class FundingCreationResourceResolver(
    private val wishRepository: WishRepository,
    private val payoutAccountRepository: PayoutAccountRepository,
    private val fundingRepository: FundingRepository,
) {

    fun resolveWish(userId: Long, wishId: Long): Wish {
        val wish = wishRepository.findByIdAndUserIdForUpdate(wishId, userId)
            ?: throw BusinessException(WishErrorCode.WISH_NOT_FOUND)

        if (fundingRepository.existsByOwnerIdAndProductSourceWishIdAndStatusNotIn(
                userId,
                wishId,
                ENDED_FUNDING_STATUSES,
            )
        ) {
            throw BusinessException(WishErrorCode.WISH_IN_FUNDING)
        }

        return wish
    }

    fun resolvePayoutAccount(userId: Long, payoutAccountId: Long?): PayoutAccount {
        val payoutAccount = if (payoutAccountId == null) {
            payoutAccountRepository.findDefaultByUserIdForUpdate(userId)
        } else {
            payoutAccountRepository.findByIdAndUserIdForUpdate(payoutAccountId, userId)
        }
            ?: throw BusinessException(PayoutAccountErrorCode.PAYOUT_ACCOUNT_NOT_FOUND)

        if (payoutAccount.verificationStatus !in AVAILABLE_ACCOUNT_STATUSES) {
            throw BusinessException(PayoutAccountErrorCode.PAYOUT_ACCOUNT_NOT_AVAILABLE)
        }

        return payoutAccount
    }

    companion object {
        private val ENDED_FUNDING_STATUSES = setOf(
            FundingStatus.COMPLETED,
            FundingStatus.CANCELLED,
        )
        private val AVAILABLE_ACCOUNT_STATUSES = setOf(
            AccountVerificationStatus.UNVERIFIED,
            AccountVerificationStatus.VERIFIED,
        )
    }
}
