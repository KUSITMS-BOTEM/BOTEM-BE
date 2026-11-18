package com.botem.domain.funding.service

import com.botem.domain.funding.dto.response.FundingDetailProductResponse
import com.botem.domain.funding.dto.response.FundingManagementActionsResponse
import com.botem.domain.funding.dto.response.FundingManagementState
import com.botem.domain.funding.dto.response.OwnerFundingDetailResponse
import com.botem.domain.funding.dto.response.OwnerFundingParticipantResponse
import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.RollingPaperMessageRepository
import com.botem.domain.user.repository.UserRepository
import com.botem.global.exception.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Service
class OwnerFundingDetailService(
    private val fundingRepository: FundingRepository,
    private val contributionRepository: ContributionRepository,
    private val rollingPaperMessageRepository: RollingPaperMessageRepository,
    private val userRepository: UserRepository,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun getManagementDetail(userId: Long, fundingId: Long): OwnerFundingDetailResponse {
        val now = LocalDateTime.now(clock)
        val funding = fundingRepository.findById(fundingId)
            .filter { it.status != FundingStatus.CANCELLED }
            .orElseThrow { BusinessException(FundingErrorCode.FUNDING_NOT_FOUND) }
        if (funding.ownerId != userId) {
            throw BusinessException(FundingErrorCode.FUNDING_OWNER_ONLY)
        }

        val contributions = contributionRepository.findAllByFundingIdOrderByCreatedAtAscIdAsc(fundingId)
            .filter { it.status != ContributionStatus.CANCELLED }
        val confirmedAmount = contributions.sumAmount(ContributionStatus.CONFIRMED)
        val participants = toParticipants(contributions)
        val participantCount = participants.maxOfOrNull(OwnerFundingParticipantResponse::order) ?: 0

        val beforeDeadline = funding.deadline.isAfter(now)
        val inProgress = beforeDeadline && funding.status in IN_PROGRESS_STATUSES && funding.earlyClosedAt == null
        val targetReached = confirmedAmount >= funding.targetAmount

        return OwnerFundingDetailResponse(
            fundingId = funding.id,
            type = funding.type,
            title = funding.title,
            story = funding.story,
            promise = funding.promise,
            product = FundingDetailProductResponse.from(funding.product),
            productChanged = funding.productChangedAt != null,
            productChangedAt = funding.productChangedAt,
            targetAmount = funding.targetAmount,
            deadline = funding.deadline,
            revealAt = funding.revealAt,
            remainingDays = if (beforeDeadline) {
                ChronoUnit.DAYS.between(now.toLocalDate(), funding.deadline.toLocalDate())
            } else {
                null
            },
            confirmedPercentage = progressPercentage(confirmedAmount, funding.targetAmount),
            pendingPercentage = progressPercentage(
                contributions.sumAmount(ContributionStatus.PENDING_CONFIRMATION),
                funding.targetAmount,
            ),
            pendingContributionCount = contributions.count { it.status == ContributionStatus.PENDING_CONFIRMATION },
            targetReached = targetReached,
            extended = funding.extensionCount > 0,
            earlyClosedAt = funding.earlyClosedAt,
            participantCount = participantCount,
            participants = participants,
            messageCount = rollingPaperMessageRepository.countByFundingId(fundingId),
            managementState = when {
                !inProgress -> FundingManagementState.CLOSED
                targetReached -> FundingManagementState.TARGET_REACHED
                participantCount == 0 -> FundingManagementState.NO_PARTICIPANTS
                else -> FundingManagementState.IN_PROGRESS
            },
            actions = FundingManagementActionsResponse(
                canCloseEarly = inProgress,
                canExtend = canExtend(funding, beforeDeadline, targetReached),
                canChangeProduct = inProgress,
                canReissueShareCard = inProgress,
            ),
        )
    }

    // 기간 연장은 마감 후 목표 미달 결과 화면에서 1회만 가능하다 (FUND-BS-018)
    private fun canExtend(funding: Funding, beforeDeadline: Boolean, targetReached: Boolean): Boolean =
        funding.type == FundingType.BIRTHDAY &&
            !beforeDeadline &&
            !targetReached &&
            funding.earlyClosedAt == null &&
            funding.extensionCount == 0 &&
            funding.status in EXTENDABLE_STATUSES

    // 로그인 참여자는 첫 참여 순서를 공유하고, 비로그인 참여는 각각 한 명으로 센다
    private fun toParticipants(contributions: List<Contribution>): List<OwnerFundingParticipantResponse> {
        val userIds = contributions.mapNotNull(Contribution::participantUserId).toSet()
        val profileImageUrls = userRepository.findAllById(userIds).associate { it.id to it.profileImageUrl }
        val orderByUserId = mutableMapOf<Long, Int>()
        var nextOrder = 1

        return contributions.map { contribution ->
            val userId = contribution.participantUserId
            val order = if (userId == null) nextOrder++ else orderByUserId.getOrPut(userId) { nextOrder++ }

            OwnerFundingParticipantResponse(
                contributionId = contribution.id,
                order = order,
                name = contribution.participantName,
                profileImageUrl = userId?.let(profileImageUrls::get),
                status = contribution.status,
                participatedAt = contribution.createdAt,
            )
        }
    }

    private fun List<Contribution>.sumAmount(status: ContributionStatus): Long =
        filter { it.status == status }.sumOf(Contribution::amount)

    companion object {
        private val IN_PROGRESS_STATUSES = setOf(FundingStatus.FUNDING, FundingStatus.CLOSED)
        private val EXTENDABLE_STATUSES = setOf(
            FundingStatus.FUNDING,
            FundingStatus.CLOSED,
            FundingStatus.GIFT_PREPARING,
        )
    }
}
