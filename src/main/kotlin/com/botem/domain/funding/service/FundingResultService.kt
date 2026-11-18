package com.botem.domain.funding.service

import com.botem.domain.funding.dto.response.FundingResultResponse
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.RollingPaperMessageRepository
import com.botem.domain.funding.repository.projection.FundingContributionSummary
import com.botem.global.exception.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

// 마감 여부는 저장된 상태가 아니라 deadline과 현재 시각으로 판단하고,
// 스케줄러 없이 결과에 접근할 때 펀딩을 잠근 채 필요한 상태 전이를 수행한다
@Service
class FundingResultService(
    private val fundingRepository: FundingRepository,
    private val contributionRepository: ContributionRepository,
    private val rollingPaperMessageRepository: RollingPaperMessageRepository,
    private val fundingContributionCloser: FundingContributionCloser,
    private val clock: Clock,
) {

    // REPEATABLE READ면 exists 조회 시점 스냅샷이 고정돼 잠금 대기 중 커밋된 확정·공개를 집계가 못 보므로,
    // READ COMMITTED로 잠금 이후 집계가 최신 커밋 값을 읽게 한다
    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun getResult(userId: Long, fundingId: Long): FundingResultResponse =
        resultOf(lockResultAccessibleFunding(userId, fundingId, LocalDateTime.now(clock)))

    // 확인 대기는 자동으로 확정하지 않는다. 개설자가 모두 처리하거나, 결과에서 제외하겠다고 선택해야 연다.
    // 제외된 확인 대기는 상태를 그대로 두어 참여자에게는 '확인 중'으로 보이고 결과 금액에는 반영되지 않는다.
    // 확인 대기로 공개를 거부해도 앞서 수행한 보태기 마감은 남아야 하므로 BusinessException에는 롤백하지 않는다
    // (그 외 BusinessException은 모두 상태 변경 전 검증에서 발생한다)
    @Transactional(noRollbackFor = [BusinessException::class])
    fun openResult(userId: Long, fundingId: Long, excludePendingContributions: Boolean): FundingResultResponse {
        val now = LocalDateTime.now(clock)
        val funding = lockResultAccessibleFunding(userId, fundingId, now)

        if (funding.resultOpenedAt == null) {
            if (!excludePendingContributions && summaryOf(funding).pendingCount > 0) {
                throw BusinessException(FundingErrorCode.PENDING_CONTRIBUTION_EXISTS)
            }
            funding.openResult(now)
        }

        return resultOf(funding)
    }

    private fun lockResultAccessibleFunding(userId: Long, fundingId: Long, now: LocalDateTime): Funding {
        val funding = fundingRepository.findByIdForUpdate(fundingId)
            ?: throw BusinessException(FundingErrorCode.FUNDING_NOT_FOUND)
        validateResultAccess(funding, userId, now)

        if (funding.resultOpenedAt == null) {
            fundingContributionCloser.close(funding, now)
        }

        return funding
    }

    private fun resultOf(funding: Funding): FundingResultResponse {
        val summary = summaryOf(funding)

        return FundingResultResponse.from(
            funding = funding,
            summary = summary,
            achievementPercentage = progressPercentage(summary.confirmedAmount, funding.targetAmount),
            messageCount = rollingPaperMessageRepository.countByFundingId(funding.id),
        )
    }

    private fun summaryOf(funding: Funding): FundingContributionSummary =
        contributionRepository.summarizeByFundingIds(listOf(funding.id)).singleOrNull()
            ?: FundingContributionSummary.empty(funding.id)

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
        if (funding.status !in RESULT_VIEWABLE_STATUSES) {
            throw BusinessException(FundingErrorCode.FUNDING_RESULT_NOT_OPENABLE)
        }
    }

    companion object {
        private val RESULT_VIEWABLE_STATUSES = setOf(
            FundingStatus.FUNDING,
            FundingStatus.CLOSED,
            FundingStatus.GIFT_PREPARING,
            FundingStatus.WAITING_GIFT_VERIFICATION,
            FundingStatus.COMPLETED,
        )
    }
}
