package com.botem.domain.funding.repository.projection

data class FundingContributionSummary(
    val fundingId: Long,
    val confirmedAmount: Long,
    val pendingAmount: Long,
    val pendingCount: Long,
    val participantCount: Long,
) {
    companion object {
        fun empty(fundingId: Long): FundingContributionSummary =
            FundingContributionSummary(
                fundingId = fundingId,
                confirmedAmount = 0L,
                pendingAmount = 0L,
                pendingCount = 0L,
                participantCount = 0L,
            )
    }
}
