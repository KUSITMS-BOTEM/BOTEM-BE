package com.botem.domain.funding.service

import com.botem.domain.funding.dto.response.FundingEarlyCloseResponse
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.FundingRepository
import com.botem.global.exception.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class FundingEarlyCloseService(
    private val fundingRepository: FundingRepository,
    private val fundingContributionCloser: FundingContributionCloser,
    private val clock: Clock,
) {

    @Transactional
    fun close(
        userId: Long,
        fundingId: Long,
    ): FundingEarlyCloseResponse {
        val funding = fundingRepository.findByIdForUpdate(fundingId)
            ?.takeIf { it.status != FundingStatus.CANCELLED }
            ?: throw BusinessException(FundingErrorCode.FUNDING_NOT_FOUND)
        if (funding.ownerId != userId) {
            throw BusinessException(FundingErrorCode.FUNDING_OWNER_ONLY)
        }

        val alreadyClosedEarly = funding.earlyClosedAt != null
        val closedAt = LocalDateTime.now(clock)
        funding.closeEarly(closedAt)
        if (!alreadyClosedEarly) {
            fundingContributionCloser.close(funding, closedAt)
        }

        return FundingEarlyCloseResponse.from(funding)
    }
}
