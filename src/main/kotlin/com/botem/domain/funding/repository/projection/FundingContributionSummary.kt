package com.botem.domain.funding.repository.projection

data class FundingContributionSummary(
    val fundingId: Long,
    val confirmedAmount: Long,
    val pendingAmount: Long,
    val pendingCount: Long,
)
