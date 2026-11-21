package com.botem.domain.funding.service

import com.botem.domain.funding.dto.response.FundingExtensionResponse
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.user.service.UserService
import com.botem.global.exception.BusinessException
import java.time.Clock
import java.time.LocalDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FundingExtensionService(
    private val fundingRepository: FundingRepository,
    private val contributionRepository: ContributionRepository,
    private val userService: UserService,
    private val fundingContributionCloser: FundingContributionCloser,
    private val clock: Clock,
) {

    @Transactional
    fun extend(
        userId: Long,
        fundingId: Long,
        newDeadline: LocalDateTime,
    ): FundingExtensionResponse {
        val requestedAt = LocalDateTime.now(clock)
        userService.lockActiveUser(userId)
        val funding = fundingRepository.findByIdForUpdate(fundingId)
            ?: throw BusinessException(FundingErrorCode.FUNDING_NOT_FOUND)

        if (funding.ownerId != userId) {
            throw BusinessException(FundingErrorCode.FUNDING_OWNER_ONLY)
        }

        val extendedAt = LocalDateTime.now(clock)
        if (fundingRepository.countActiveFundings(userId, extendedAt) >= MAX_ACTIVE_FUNDING_COUNT) {
            throw BusinessException(FundingErrorCode.ACTIVE_FUNDING_LIMIT_EXCEEDED)
        }

        // 기존 마감 시점까지 송금하지 않은 보탬은 결과 조회 경로와 동일하게 메시지 전용으로 확정한다
        fundingContributionCloser.close(funding, extendedAt)
        val confirmedAmount = contributionRepository.sumAmountByFundingIdAndStatus(
            fundingId = fundingId,
            status = ContributionStatus.CONFIRMED,
        )
        funding.extendDeadline(
            newDeadline = newDeadline,
            requestedAt = requestedAt,
            extendedAt = extendedAt,
            confirmedAmount = confirmedAmount,
        )

        return FundingExtensionResponse.from(funding)
    }


    companion object {
        private const val MAX_ACTIVE_FUNDING_COUNT = 5
    }
}
