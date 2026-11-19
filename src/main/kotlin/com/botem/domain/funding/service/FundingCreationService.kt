package com.botem.domain.funding.service

import com.botem.domain.funding.dto.request.CreateBirthdayFundingRequest
import com.botem.domain.funding.dto.request.CreateSurpriseFundingRequest
import com.botem.domain.funding.dto.response.CreateFundingResponse
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.user.entity.User
import com.botem.domain.user.entity.UserStatus
import com.botem.domain.user.exception.UserErrorCode
import com.botem.domain.user.repository.UserRepository
import com.botem.domain.wish.entity.Wish
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
        // 입력만으로 판단하는 계좌번호 검증은 잠금 전에 끝낸다
        val payoutAccount = resourceResolver.resolveDirectPayoutAccount(request.bank, request.accountNumber)
        lockUser(userId)
        val now = LocalDateTime.now(clock)
        validateActiveFundingCount(userId, now)

        val wish = resourceResolver.resolveWish(userId, request.wishId)
        val funding = Funding.createBirthday(
            ownerId = userId,
            targetAmount = request.targetAmount,
            deadline = request.deadline.atTime(LocalTime.MAX).truncatedTo(ChronoUnit.MICROS),
            story = request.story,
            promise = request.promise,
            product = wish.toSnapshot(),
            payoutAccount = payoutAccount,
            now = now,
        )
        val savedFunding = fundingRepository.save(funding)

        return CreateFundingResponse.from(savedFunding, createShareUrl(savedFunding.id))
    }

    @Transactional
    fun createSurpriseFunding(
        userId: Long,
        request: CreateSurpriseFundingRequest,
    ): CreateFundingResponse {
        if (userId == request.recipientId) {
            throw BusinessException(FundingErrorCode.SURPRISE_SELF_RECIPIENT_NOT_ALLOWED)
        }
        // 입력만으로 판단하는 계좌번호 검증은 잠금 전에 끝낸다
        val payoutAccount = resourceResolver.resolveDirectPayoutAccount(request.bank, request.accountNumber)
        // 서로에게 동시에 서프라이즈를 여는 요청의 교착을 막기 위해 ID 순서로 잠근다
        val lockedUsers = listOf(userId, request.recipientId).sorted()
            .associateWith { userRepository.findByIdForUpdate(it) }
        validateActiveUser(lockedUsers[userId])
        if (lockedUsers[request.recipientId]?.status != UserStatus.ACTIVE) {
            throw BusinessException(UserErrorCode.USER_NOT_FOUND)
        }

        val now = LocalDateTime.now(clock)
        validateActiveFundingCount(userId, now)
        // 개설자 진행 중 한도와 같이, 공개 시각이 지난 서프라이즈는 중복으로 보지 않는다
        if (fundingRepository.existsByRecipientIdAndTypeAndRevealAtAfterAndStatusNotIn(
                request.recipientId,
                FundingType.SURPRISE,
                now,
                ENDED_FUNDING_STATUSES,
            )
        ) {
            throw BusinessException(FundingErrorCode.SURPRISE_FUNDING_ALREADY_EXISTS)
        }

        val wish = resourceResolver.resolveWish(request.recipientId, request.wishId)
        val funding = Funding.createSurprise(
            ownerId = userId,
            recipientId = request.recipientId,
            targetAmount = request.targetAmount,
            deadline = request.deadline.atTime(LocalTime.MAX).truncatedTo(ChronoUnit.MICROS),
            revealAt = request.revealAt.truncatedTo(ChronoUnit.MICROS),
            showOnRecipientProfile = request.showOnRecipientProfile,
            notifyRecipientFriends = request.notifyRecipientFriends,
            product = wish.toSnapshot(),
            payoutAccount = payoutAccount,
            now = now,
        )
        val savedFunding = fundingRepository.save(funding)

        return CreateFundingResponse.from(savedFunding, createShareUrl(savedFunding.id))
    }

    private fun lockUser(userId: Long) {
        validateActiveUser(userRepository.findByIdForUpdate(userId))
    }

    private fun validateActiveUser(user: User?) {
        if (user == null) {
            throw BusinessException(UserErrorCode.USER_NOT_FOUND)
        }
        if (user.status != UserStatus.ACTIVE) {
            throw BusinessException(AuthErrorCode.WITHDRAWN_USER)
        }
    }

    private fun Wish.toSnapshot(): FundingProductSnapshot =
        FundingProductSnapshot(
            sourceWishId = id,
            name = productName,
            imageUrl = productImageUrl,
            price = price,
            url = productUrl,
        )

    private fun validateActiveFundingCount(userId: Long, now: LocalDateTime) {
        if (fundingRepository.countActiveFundings(userId, now) >= MAX_ACTIVE_FUNDING_COUNT) {
            throw BusinessException(FundingErrorCode.ACTIVE_FUNDING_LIMIT_EXCEEDED)
        }
    }

    private fun createShareUrl(fundingId: Long): String =
        "${fundingShareProperties.baseUrl.trimEnd('/')}/$fundingId"

    companion object {
        private const val MAX_ACTIVE_FUNDING_COUNT = 5
        private val ENDED_FUNDING_STATUSES = setOf(
            FundingStatus.COMPLETED,
            FundingStatus.CANCELLED,
        )
    }
}
