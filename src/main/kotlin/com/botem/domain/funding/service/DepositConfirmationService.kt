package com.botem.domain.funding.service

import com.botem.domain.funding.dto.response.ConfirmAllContributionsResponse
import com.botem.domain.funding.dto.response.ContributionDecisionResponse
import com.botem.domain.funding.dto.response.FundingProgressResponse
import com.botem.domain.funding.dto.response.PendingContributionListResponse
import com.botem.domain.funding.dto.response.PendingContributionResponse
import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.projection.FundingContributionSummary
import com.botem.domain.user.repository.UserRepository
import com.botem.global.exception.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

// 개설자가 은행 앱에서 입금을 확인한 뒤 직접 확정하거나 미입금 처리한다 (FUND-BS-007)
@Service
class DepositConfirmationService(
    private val fundingRepository: FundingRepository,
    private val contributionRepository: ContributionRepository,
    private val userRepository: UserRepository,
    private val fundingContributionCloser: FundingContributionCloser,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun getPendingContributions(userId: Long, fundingId: Long): PendingContributionListResponse {
        val funding = fundingRepository.findById(fundingId)
            .orElseThrow { BusinessException(FundingErrorCode.FUNDING_NOT_FOUND) }
        validateOwner(funding, userId)

        val pendingContributions = findPendingContributions(fundingId)
        val userIds = pendingContributions.mapNotNull(Contribution::participantUserId).toSet()
        val profileImageUrls = userRepository.findAllById(userIds).associate { it.id to it.profileImageUrl }

        return PendingContributionListResponse(
            pendingContributionCount = pendingContributions.size,
            contributions = pendingContributions.map { contribution ->
                PendingContributionResponse(
                    contributionId = contribution.id,
                    participantName = contribution.participantName,
                    profileImageUrl = contribution.participantUserId?.let(profileImageUrls::get),
                    participatedAt = contribution.createdAt,
                )
            },
        )
    }

    @Transactional
    fun confirm(userId: Long, fundingId: Long, contributionId: Long): ContributionDecisionResponse {
        val funding = lockChangeableFunding(userId, fundingId)
        val contribution = lockContribution(fundingId, contributionId)

        // 확정 금액이 목표를 넘더라도 이미 송금된 참여이므로 막지 않는다
        val now = LocalDateTime.now(clock)
        contribution.confirm(now)

        return ContributionDecisionResponse(contribution.id, contribution.status, progressAfterConfirm(funding, now))
    }

    @Transactional
    fun reject(userId: Long, fundingId: Long, contributionId: Long): ContributionDecisionResponse {
        val funding = lockChangeableFunding(userId, fundingId)
        val contribution = lockContribution(fundingId, contributionId)

        contribution.reject()

        return ContributionDecisionResponse(contribution.id, contribution.status, progressOf(funding))
    }

    @Transactional
    fun confirmAll(userId: Long, fundingId: Long): ConfirmAllContributionsResponse {
        val funding = lockChangeableFunding(userId, fundingId)
        val now = LocalDateTime.now(clock)

        // 참여 행도 잠가 같은 행의 비회원 비밀번호 실패 기록을 조회 당시 값으로 덮어쓰지 않게 한다
        val pendingContributions = contributionRepository.findAllByFundingIdAndStatusForUpdate(
            fundingId,
            ContributionStatus.PENDING_CONFIRMATION,
        )
        pendingContributions.forEach { it.confirm(now) }

        return ConfirmAllContributionsResponse(pendingContributions.size, progressAfterConfirm(funding, now))
    }

    // 펀딩을 잠가 같은 펀딩의 확인·미입금·전체 확인 요청과 새 보태기를 직렬화한다
    private fun lockChangeableFunding(userId: Long, fundingId: Long): Funding {
        val funding = fundingRepository.findByIdForUpdate(fundingId)
            ?: throw BusinessException(FundingErrorCode.FUNDING_NOT_FOUND)
        validateOwner(funding, userId)
        // 결과를 열었거나 서프라이즈 선물 준비를 마친 뒤에는 확정 금액이 바뀌지 않도록 막는다
        if (funding.resultOpenedAt != null || funding.isSurpriseGiftPrepared()) {
            throw BusinessException(FundingErrorCode.FUNDING_RESULT_ALREADY_OPENED)
        }

        return funding
    }

    private fun validateOwner(funding: Funding, userId: Long) {
        if (funding.status == FundingStatus.CANCELLED) {
            throw BusinessException(FundingErrorCode.FUNDING_NOT_FOUND)
        }
        if (funding.ownerId != userId) {
            throw BusinessException(FundingErrorCode.FUNDING_OWNER_ONLY)
        }
    }

    private fun lockContribution(fundingId: Long, contributionId: Long): Contribution =
        contributionRepository.findByIdAndFundingIdForUpdate(contributionId, fundingId)
            ?: throw BusinessException(FundingErrorCode.CONTRIBUTION_NOT_FOUND)

    private fun findPendingContributions(fundingId: Long): List<Contribution> =
        contributionRepository.findAllByFundingIdAndStatusOrderByCreatedAtAscIdAsc(
            fundingId,
            ContributionStatus.PENDING_CONFIRMATION,
        )

    // 확정 금액이 목표에 닿으면 보태기를 자동 마감한다 (FUND-PG-006(2), 10/24 확정)
    private fun progressAfterConfirm(funding: Funding, now: LocalDateTime): FundingProgressResponse {
        val progress = progressOf(funding)
        if (progress.targetReached) {
            fundingContributionCloser.close(funding, now)
        }

        return progress
    }

    // 변경 내용은 집계 쿼리 전에 자동 flush되어 반영된다.
    // 송금 전 기록의 메시지 전용 전환은 확정·대기 금액에 영향을 주지 않으므로 마감 전후 진행률이 같다
    private fun progressOf(funding: Funding): FundingProgressResponse {
        val summary = contributionRepository.summarizeByFundingIds(listOf(funding.id)).firstOrNull()
            ?: FundingContributionSummary.empty(funding.id)

        return FundingProgressResponse(
            confirmedPercentage = progressPercentage(summary.confirmedAmount, funding.targetAmount),
            pendingPercentage = progressPercentage(summary.pendingAmount, funding.targetAmount),
            pendingContributionCount = summary.pendingCount,
            targetReached = summary.confirmedAmount >= funding.targetAmount,
        )
    }
}
