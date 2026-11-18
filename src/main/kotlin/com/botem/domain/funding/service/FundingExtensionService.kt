package com.botem.domain.funding.service

import com.botem.domain.funding.dto.response.FundingExtensionResponse
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.global.exception.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class FundingExtensionService(
    private val fundingRepository: FundingRepository,
    private val contributionRepository: ContributionRepository,
    private val clock: Clock,
) {

    @Transactional
    fun extend(
        userId: Long,
        fundingId: Long,
        newDeadline: LocalDateTime,
    ): FundingExtensionResponse {
        val funding = fundingRepository.findByIdForUpdate(fundingId)
            ?: throw BusinessException(FundingErrorCode.FUNDING_NOT_FOUND)

        if (funding.ownerId != userId) {
            throw BusinessException(FundingErrorCode.FUNDING_OWNER_ONLY)
        }

        val confirmedAmount = contributionRepository.sumAmountByFundingIdAndStatus(
            fundingId = fundingId,
            status = ContributionStatus.CONFIRMED,
        )
        funding.extendDeadline(
            newDeadline = newDeadline,
            extendedAt = LocalDateTime.now(clock),
            confirmedAmount = confirmedAmount,
        )

        return FundingExtensionResponse.from(funding)
    }
}
