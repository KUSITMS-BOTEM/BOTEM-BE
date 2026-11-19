package com.botem.domain.funding.dto.request

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

enum class ContributionDecision {
    CONFIRM,
    REJECT,
}

// 개별 선택과 전체 선택을 같은 요청으로 처리한다 (FUND-BS-007)
data class DecideContributionsRequest(
    @field:NotEmpty
    @field:Size(max = MAX_CONTRIBUTION_IDS)
    val contributionIds: List<Long>,

    val decision: ContributionDecision,
)

data class UndoContributionDecisionsRequest(
    @field:NotEmpty
    @field:Size(max = MAX_CONTRIBUTION_IDS)
    val contributionIds: List<Long>,
)

private const val MAX_CONTRIBUTION_IDS = 100
