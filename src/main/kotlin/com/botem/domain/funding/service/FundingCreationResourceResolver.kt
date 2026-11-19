package com.botem.domain.funding.service

import com.botem.domain.account.entity.AccountVerificationStatus
import com.botem.domain.account.entity.Bank
import com.botem.domain.account.entity.PayoutAccount
import com.botem.domain.account.exception.PayoutAccountErrorCode
import com.botem.domain.account.repository.PayoutAccountRepository
import com.botem.domain.account.service.AccountNumberCipher
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
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
    private val accountNumberCipher: AccountNumberCipher,
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

    // 입력한 계좌는 내 계좌 목록에 등록하지 않고 펀딩 스냅샷으로만 저장한다
    fun resolveDirectPayoutAccount(bank: Bank, accountNumber: String): FundingPayoutAccountSnapshot =
        FundingPayoutAccountSnapshot(
            sourcePayoutAccountId = null,
            bank = bank,
            encryptedAccountNumber = accountNumberCipher.normalizeAndEncrypt(accountNumber),
            accountHolder = null,
        )

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
