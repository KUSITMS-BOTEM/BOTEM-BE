package com.botem.domain.funding.dto.response

import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.entity.FundingType
import java.time.LocalDateTime

// 개설자 전용 펀딩 관리 화면. 개설자에게도 참여자별 금액은 반환하지 않는다
data class OwnerFundingDetailResponse(
    val fundingId: Long,
    val type: FundingType,
    val title: String?,
    val story: String?,
    val promise: String?,
    val product: FundingDetailProductResponse,
    val productChanged: Boolean,
    val productChangedAt: LocalDateTime?,
    val targetAmount: Long,
    val deadline: LocalDateTime,
    val revealAt: LocalDateTime?,
    val remainingDays: Long?,
    val confirmedPercentage: Int,
    val pendingPercentage: Int,
    val pendingContributionCount: Int,
    val targetReached: Boolean,
    val extended: Boolean,
    val earlyClosedAt: LocalDateTime?,
    val participantCount: Int,
    val participants: List<OwnerFundingParticipantResponse>,
    val messageCount: Long,
    val managementState: FundingManagementState,
    val actions: FundingManagementActionsResponse,
)

// 입금 대조를 위해 참여 기록마다 한 줄. order는 사람 기준 첫 참여 순서로 역할 배지에 쓴다
data class OwnerFundingParticipantResponse(
    val contributionId: Long,
    val order: Int,
    val name: String,
    val profileImageUrl: String?,
    val status: ContributionStatus,
    val participatedAt: LocalDateTime,
)

data class FundingManagementActionsResponse(
    val canCloseEarly: Boolean,
    val canExtend: Boolean,
    val canChangeProduct: Boolean,
    val canReissueShareCard: Boolean,
)

enum class FundingManagementState {
    NO_PARTICIPANTS,
    IN_PROGRESS,
    TARGET_REACHED,
    CLOSED,
}
