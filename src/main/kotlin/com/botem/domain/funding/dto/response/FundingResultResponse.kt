package com.botem.domain.funding.dto.response

import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.repository.projection.FundingContributionSummary
import com.botem.domain.funding.service.progressPercentage
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
    val messageCount: Long,
    val pendingContributionCount: Long,
    val pendingCleanupRequired: Boolean,
    val resultOpenedAt: LocalDateTime?,
) {
    companion object {
        fun from(
            funding: Funding,
            summary: FundingContributionSummary,
            messageCount: Long,
        ): FundingResultResponse =
            FundingResultResponse(
                fundingId = funding.id,
                productName = funding.product.name,
                productImageUrl = funding.product.imageUrl,
                targetAmount = funding.targetAmount,
                confirmedAmount = summary.confirmedAmount,
                achievementPercentage = progressPercentage(summary.confirmedAmount, funding.targetAmount),
                targetReached = summary.confirmedAmount >= funding.targetAmount,
                participantCount = summary.participantCount,
                messageCount = messageCount,
                pendingContributionCount = summary.pendingCount,
                pendingCleanupRequired = funding.resultOpenedAt == null && summary.pendingCount > 0,
                resultOpenedAt = funding.resultOpenedAt,
            )
    }
}
