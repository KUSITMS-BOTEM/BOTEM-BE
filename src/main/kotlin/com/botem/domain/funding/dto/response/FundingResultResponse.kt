package com.botem.domain.funding.dto.response

import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.repository.projection.FundingContributionSummary
import java.time.LocalDateTime

data class FundingResultResponse(
    val fundingId: Long,
    val productName: String,
    val productImageUrl: String,
    val targetAmount: Long,
    val confirmedAmount: Long,
    val achievementPercentage: Int,
    val targetReached: Boolean,
    val participantCount: Long,
    val resultOpenedAt: LocalDateTime?,
) {
    companion object {
        fun from(
            funding: Funding,
            summary: FundingContributionSummary,
            achievementPercentage: Int,
        ): FundingResultResponse =
            FundingResultResponse(
                fundingId = funding.id,
                productName = funding.product.name,
                productImageUrl = funding.product.imageUrl,
                targetAmount = funding.targetAmount,
                confirmedAmount = summary.confirmedAmount,
                achievementPercentage = achievementPercentage,
                targetReached = summary.confirmedAmount >= funding.targetAmount,
                participantCount = summary.participantCount,
                resultOpenedAt = funding.resultOpenedAt,
            )
    }
}
