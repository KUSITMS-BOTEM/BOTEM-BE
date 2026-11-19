package com.botem.domain.funding.service

import com.botem.domain.funding.dto.request.SendContributionThankCardRequest
import com.botem.domain.funding.dto.response.ContributionThankCardResponse
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.entity.ContributionThankCard
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.ContributionThankCardRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class ContributionThankCardService(
    private val fundingRepository: FundingRepository,
    private val contributionRepository: ContributionRepository,
    private val contributionThankCardRepository: ContributionThankCardRepository,
    private val clock: Clock,
) {

    @Transactional
    fun send(
        userId: Long,
        fundingId: Long,
        contributionId: Long,
        request: SendContributionThankCardRequest,
    ): ContributionThankCardResponse {
        val funding = fundingRepository.findByIdForUpdate(fundingId)
            ?.takeIf { it.status != FundingStatus.CANCELLED }
            ?: throw BusinessException(FundingErrorCode.FUNDING_NOT_FOUND)
        if (funding.ownerId != userId) {
            throw BusinessException(FundingErrorCode.FUNDING_OWNER_ONLY)
        }

        val contribution = contributionRepository.findByIdAndFundingIdForUpdate(contributionId, fundingId)
            ?: throw BusinessException(FundingErrorCode.CONTRIBUTION_NOT_FOUND)
        if (contribution.status != ContributionStatus.CONFIRMED) {
            throw BusinessException(FundingErrorCode.CONTRIBUTION_THANK_CARD_CONFIRMED_ONLY)
        }

        contributionThankCardRepository.findByContributionId(contributionId)?.let {
            return ContributionThankCardResponse.from(it)
        }

        val message = normalizeMessage(request.message)
        val thankCard = contributionThankCardRepository.save(
            ContributionThankCard(
                contributionId = contributionId,
                senderUserId = userId,
                message = message,
                sentAt = LocalDateTime.now(clock),
            ),
        )

        return ContributionThankCardResponse.from(thankCard)
    }

    private fun normalizeMessage(message: String): String {
        val normalized = message.trim { it.isWhitespace() || Character.isSpaceChar(it) }
        if (normalized.isEmpty() || normalized.length > ContributionThankCard.MAX_MESSAGE_LENGTH) {
            throw BusinessException(
                CommonErrorCode.INVALID_INPUT_VALUE,
                "message: ${ContributionThankCard.MAX_MESSAGE_LENGTH}자 이내로 입력해 주세요",
            )
        }
        return normalized
    }
}
