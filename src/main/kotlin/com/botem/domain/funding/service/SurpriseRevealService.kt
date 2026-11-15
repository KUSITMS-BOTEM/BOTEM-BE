package com.botem.domain.funding.service

import com.botem.domain.funding.dto.request.SendSurpriseThankCardRequest
import com.botem.domain.funding.dto.response.SurpriseRevealDetailResponse
import com.botem.domain.funding.dto.response.SurpriseThankCardResponse
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.SurpriseThankCard
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.RollingPaperMessageRepository
import com.botem.domain.funding.repository.SurpriseThankCardRepository
import com.botem.domain.funding.repository.projection.FundingContributionSummary
import com.botem.domain.user.exception.UserErrorCode
import com.botem.domain.user.repository.UserRepository
import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class SurpriseRevealService(
    private val fundingRepository: FundingRepository,
    private val contributionRepository: ContributionRepository,
    private val rollingPaperMessageRepository: RollingPaperMessageRepository,
    private val surpriseThankCardRepository: SurpriseThankCardRepository,
    private val userRepository: UserRepository,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun getDetail(userId: Long, fundingId: Long): SurpriseRevealDetailResponse {
        val funding = fundingRepository.findById(fundingId)
            .orElseThrow { BusinessException(FundingErrorCode.FUNDING_NOT_FOUND) }
        funding.validateSurpriseRevealAccess(userId, LocalDateTime.now(clock))

        return createDetail(userId, funding)
    }

    @Transactional
    fun open(userId: Long, fundingId: Long): SurpriseRevealDetailResponse {
        val now = LocalDateTime.now(clock)
        val funding = fundingRepository.findByIdForUpdate(fundingId)
            ?: throw BusinessException(FundingErrorCode.FUNDING_NOT_FOUND)
        funding.revealSurprise(userId, now)

        return createDetail(userId, funding)
    }

    @Transactional
    fun sendThankCard(
        userId: Long,
        fundingId: Long,
        request: SendSurpriseThankCardRequest,
    ): SurpriseThankCardResponse {
        val funding = fundingRepository.findByIdForUpdate(fundingId)
            ?: throw BusinessException(FundingErrorCode.FUNDING_NOT_FOUND)
        funding.validateSurpriseRevealAccess(userId, LocalDateTime.now(clock))
        if (funding.surpriseOpenedAt == null) {
            throw BusinessException(FundingErrorCode.SURPRISE_NOT_OPENED)
        }

        surpriseThankCardRepository.findByFundingId(fundingId)?.let {
            return SurpriseThankCardResponse.from(it)
        }

        val message = request.message.trim { it.isWhitespace() || Character.isSpaceChar(it) }
        if (message.isEmpty() || message.length > MAX_THANK_CARD_MESSAGE_LENGTH) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT_VALUE)
        }
        val thankCard = surpriseThankCardRepository.save(
            SurpriseThankCard(
                fundingId = fundingId,
                senderUserId = userId,
                message = message,
                sentAt = LocalDateTime.now(clock),
            ),
        )

        return SurpriseThankCardResponse.from(thankCard)
    }

    private fun createDetail(userId: Long, funding: Funding): SurpriseRevealDetailResponse {
        val fundingId = funding.id

        val recipient = userRepository.findById(userId)
            .orElseThrow { BusinessException(UserErrorCode.USER_NOT_FOUND) }
        val organizer = userRepository.findById(funding.ownerId)
            .orElseThrow { BusinessException(UserErrorCode.USER_NOT_FOUND) }
        val summary = contributionRepository.summarizeByFundingIds(listOf(fundingId))
            .singleOrNull()
            ?: FundingContributionSummary.empty(fundingId)
        val messages = rollingPaperMessageRepository.findAllByFundingId(fundingId)

        return SurpriseRevealDetailResponse.from(
            funding = funding,
            recipientName = recipient.name ?: recipient.nickname,
            organizerName = organizer.name ?: organizer.nickname,
            summary = summary,
            messages = messages,
            thankCardSent = surpriseThankCardRepository.existsByFundingId(fundingId),
        )
    }

    companion object {
        private const val MAX_THANK_CARD_MESSAGE_LENGTH = 1000
    }
}
