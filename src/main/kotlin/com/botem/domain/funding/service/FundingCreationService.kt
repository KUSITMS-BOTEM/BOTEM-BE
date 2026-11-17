package com.botem.domain.funding.service

import com.botem.domain.funding.dto.request.CreateBirthdayFundingRequest
import com.botem.domain.funding.dto.response.CreateFundingResponse
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.user.entity.UserStatus
import com.botem.domain.user.exception.UserErrorCode
import com.botem.domain.user.repository.UserRepository
import com.botem.global.exception.AuthErrorCode
import com.botem.global.exception.BusinessException
import com.botem.global.property.FundingShareProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

@Service
class FundingCreationService(
    private val fundingRepository: FundingRepository,
    private val userRepository: UserRepository,
    private val resourceResolver: FundingCreationResourceResolver,
    private val fundingShareProperties: FundingShareProperties,
    private val clock: Clock,
) {

    @Transactional
    fun createBirthdayFunding(
        userId: Long,
        request: CreateBirthdayFundingRequest,
    ): CreateFundingResponse {
        lockUser(userId)
        val now = LocalDateTime.now(clock)
        validateActiveFundingCount(userId, now)

        val wish = resourceResolver.resolveWish(userId, request.wishId)
        val payoutAccount = resourceResolver.resolvePayoutAccount(userId, request.payoutAccountId)
        val funding = Funding.createBirthday(
            ownerId = userId,
            title = request.title,
            targetAmount = request.targetAmount,
            deadline = request.deadline.atTime(LocalTime.MAX).truncatedTo(ChronoUnit.MICROS),
            story = request.story,
            promise = request.promise,
            product = FundingProductSnapshot(
                sourceWishId = wish.id,
                name = wish.productName,
                imageUrl = wish.productImageUrl,
                price = wish.price,
                url = wish.productUrl,
            ),
            payoutAccount = FundingPayoutAccountSnapshot(
                sourcePayoutAccountId = payoutAccount.id,
                bank = payoutAccount.bank,
                encryptedAccountNumber = payoutAccount.encryptedAccountNumber,
                accountHolder = payoutAccount.accountHolder,
            ),
            now = now,
        )
        val savedFunding = fundingRepository.save(funding)

        return CreateFundingResponse.from(savedFunding, createShareUrl(savedFunding.id))
    }

    private fun lockUser(userId: Long) {
        val user = userRepository.findByIdForUpdate(userId)
            ?: throw BusinessException(UserErrorCode.USER_NOT_FOUND)
        if (user.status != UserStatus.ACTIVE) {
            throw BusinessException(AuthErrorCode.WITHDRAWN_USER)
        }
    }

    private fun validateActiveFundingCount(userId: Long, now: LocalDateTime) {
        if (fundingRepository.countActiveFundings(userId, now) >= MAX_ACTIVE_FUNDING_COUNT) {
            throw BusinessException(FundingErrorCode.ACTIVE_FUNDING_LIMIT_EXCEEDED)
        }
    }

    private fun createShareUrl(fundingId: Long): String =
        "${fundingShareProperties.baseUrl.trimEnd('/')}/$fundingId"

    companion object {
        private const val MAX_ACTIVE_FUNDING_COUNT = 5
    }
}
