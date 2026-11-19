package com.botem.domain.funding.dto.response

import com.botem.domain.funding.entity.ContributionStatus
import java.time.LocalDateTime

// 입금 확인 대기 목록. 개설자가 통장 입금 내역과 대조하도록 이름·참여 시각·금액을 반환한다
data class PendingContributionListResponse(
    val pendingContributionCount: Int,
    val contributions: List<PendingContributionResponse>,
)

data class PendingContributionResponse(
    val contributionId: Long,
    val participantName: String,
    val profileImageUrl: String?,
    val participatedAt: LocalDateTime,
    val amount: Long,
)

data class ContributionDecisionResponse(
    val contributionId: Long,
    val status: ContributionStatus,
    val progress: FundingProgressResponse,
)

// 선택 처리와 실행 취소는 요청한 보탬마다 처리 후 상태를 돌려준다
data class ContributionDecisionsResponse(
    val contributions: List<ContributionDecisionResult>,
    val progress: FundingProgressResponse,
)

data class ContributionDecisionResult(
    val contributionId: Long,
    val status: ContributionStatus,
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
