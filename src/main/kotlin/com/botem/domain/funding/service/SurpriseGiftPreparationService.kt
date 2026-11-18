package com.botem.domain.funding.service

import com.botem.domain.funding.dto.response.SurpriseGiftPreparationResponse
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.RollingPaperMessageRepository
import com.botem.domain.funding.repository.projection.FundingContributionSummary
import com.botem.domain.user.repository.UserRepository
import com.botem.global.exception.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

// 생일 펀딩 결과(FundingResultService)와 같이 스케줄러 없이 접근 시점에 펀딩을 잠그고 보태기 마감을 수행한다
@Service
class SurpriseGiftPreparationService(
    private val fundingRepository: FundingRepository,
    private val contributionRepository: ContributionRepository,
    private val rollingPaperMessageRepository: RollingPaperMessageRepository,
    private val userRepository: UserRepository,
    private val fundingContributionCloser: FundingContributionCloser,
    private val clock: Clock,
) {

    // 잠금 이후 집계가 최신 커밋 값을 읽도록 READ COMMITTED를 사용한다
    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun getPreparation(userId: Long, fundingId: Long): SurpriseGiftPreparationResponse =
        responseOf(lockPreparationAccessibleFunding(userId, fundingId, LocalDateTime.now(clock)))

    // 확인 대기는 자동으로 확정하지 않는다. 모두 처리하거나 제외하겠다고 선택해야 준비를 완료한다.
    // 확인 대기로 거부해도 앞서 수행한 보태기 마감은 남아야 하므로 BusinessException에는 롤백하지 않는다
    @Transactional(noRollbackFor = [BusinessException::class])
    fun prepare(
        userId: Long,
        fundingId: Long,
        excludePendingContributions: Boolean,
    ): SurpriseGiftPreparationResponse {
        val now = LocalDateTime.now(clock)
        val funding = lockPreparationAccessibleFunding(userId, fundingId, now)

        if (funding.giftPreparedAt == null) {
            if (!excludePendingContributions && summaryOf(funding).pendingCount > 0) {
                throw BusinessException(FundingErrorCode.PENDING_CONTRIBUTION_EXISTS)
            }
            funding.prepareSurpriseGift(now)
        }

        return responseOf(funding)
    }

    private fun lockPreparationAccessibleFunding(userId: Long, fundingId: Long, now: LocalDateTime): Funding {
        val funding = fundingRepository.findByIdForUpdate(fundingId)
            ?.takeIf { it.status != FundingStatus.CANCELLED }
            ?: throw BusinessException(FundingErrorCode.FUNDING_NOT_FOUND)
        if (funding.ownerId != userId) {
            throw BusinessException(FundingErrorCode.FUNDING_OWNER_ONLY)
        }
        if (funding.type != FundingType.SURPRISE) {
            throw BusinessException(FundingErrorCode.SURPRISE_FUNDING_REQUIRED)
        }
        if (!funding.isContributionClosed(now)) {
            throw BusinessException(FundingErrorCode.FUNDING_NOT_ENDED)
        }

        if (funding.giftPreparedAt == null) {
            fundingContributionCloser.close(funding, now)
        }

        return funding
    }

    private fun responseOf(funding: Funding): SurpriseGiftPreparationResponse {
        val summary = summaryOf(funding)
        val recipientName = funding.recipientId
            ?.let { userRepository.findById(it).orElse(null) }
            ?.let { it.name ?: it.nickname }

        return SurpriseGiftPreparationResponse.from(
            funding = funding,
            recipientName = recipientName,
            summary = summary,
            achievementPercentage = progressPercentage(summary.confirmedAmount, funding.targetAmount),
            messageCount = rollingPaperMessageRepository.countByFundingId(funding.id),
        )
    }

    private fun summaryOf(funding: Funding): FundingContributionSummary =
        contributionRepository.summarizeByFundingIds(listOf(funding.id)).singleOrNull()
            ?: FundingContributionSummary.empty(funding.id)
}
