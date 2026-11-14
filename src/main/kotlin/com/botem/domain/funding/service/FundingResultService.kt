package com.botem.domain.funding.service

import com.botem.domain.funding.dto.response.FundingResultResponse
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.projection.FundingContributionSummary
import com.botem.global.exception.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDateTime

@Service
class FundingResultService(
    private val fundingRepository: FundingRepository,
    private val contributionRepository: ContributionRepository,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun getResult(userId: Long, fundingId: Long): FundingResultResponse {
        val funding = fundingRepository.findById(fundingId)
            .orElseThrow { BusinessException(FundingErrorCode.FUNDING_NOT_FOUND) }
        validateResultAccess(funding, userId, LocalDateTime.now(clock))
        val summary = contributionRepository.summarizeByFundingIds(listOf(fundingId))
            .singleOrNull()
            ?: FundingContributionSummary.empty(fundingId)

        return FundingResultResponse.from(
            funding = funding,
            summary = summary,
            achievementPercentage = percentage(summary.confirmedAmount, funding.targetAmount),
        )
    }

    private fun validateResultAccess(
        funding: Funding,
        userId: Long,
        now: LocalDateTime,
    ) {
        if (funding.ownerId != userId) {
            throw BusinessException(FundingErrorCode.FUNDING_OWNER_ONLY)
        }
        if (funding.type != FundingType.BIRTHDAY) {
            throw BusinessException(FundingErrorCode.BIRTHDAY_FUNDING_REQUIRED)
        }
        if (funding.deadline.isAfter(now)) {
            throw BusinessException(FundingErrorCode.FUNDING_NOT_ENDED)
        }
        if (funding.status == FundingStatus.CANCELLED) {
            throw BusinessException(FundingErrorCode.FUNDING_RESULT_NOT_OPENABLE)
        }
    }

    private fun percentage(amount: Long, targetAmount: Long): Int {
        if (amount <= 0L || targetAmount <= 0L) {
            return 0
        }

        return BigDecimal.valueOf(amount)
            .multiply(ONE_HUNDRED)
            .divide(BigDecimal.valueOf(targetAmount), 0, RoundingMode.DOWN)
            .min(MAX_INT)
            .toInt()
    }

    companion object {
        private val ONE_HUNDRED = BigDecimal.valueOf(100L)
        private val MAX_INT = BigDecimal.valueOf(Int.MAX_VALUE.toLong())
    }
}
