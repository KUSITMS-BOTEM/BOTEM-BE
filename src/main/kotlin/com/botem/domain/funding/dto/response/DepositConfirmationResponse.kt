package com.botem.domain.funding.dto.response

import com.botem.domain.funding.entity.ContributionStatus
import java.time.LocalDateTime

// 입금 확인 대기 목록. 개설자에게도 개별 금액은 반환하지 않고 이름과 참여 시각으로 대조한다
data class PendingContributionListResponse(
    val pendingContributionCount: Int,
    val contributions: List<PendingContributionResponse>,
)

data class PendingContributionResponse(
    val contributionId: Long,
    val participantName: String,
    val profileImageUrl: String?,
    val participatedAt: LocalDateTime,
)

data class ContributionDecisionResponse(
    val contributionId: Long,
    val status: ContributionStatus,
    val progress: FundingProgressResponse,
)

data class ConfirmAllContributionsResponse(
    val confirmedCount: Int,
    val progress: FundingProgressResponse,
)

// 확인 결과가 바로 화면 게이지에 반영되도록 처리 후 진행률을 함께 반환한다
data class FundingProgressResponse(
    val confirmedPercentage: Int,
    val pendingPercentage: Int,
    val pendingContributionCount: Long,
    val targetReached: Boolean,
)
