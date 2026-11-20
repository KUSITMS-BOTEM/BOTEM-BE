package com.botem.domain.funding.dto.response

import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.repository.projection.FundingContributionSummary
import java.time.LocalDateTime

data class FundingResultResponse(
    val fundingId: Long,
    val productName: String,
    val productImageUrl: String,
    val productUrl: String,
    val productChanged: Boolean,
    val targetAmount: Long,
    val confirmedAmount: Long,
    val achievementPercentage: Int,
    val targetReached: Boolean,
    val participantCount: Long,
    val messageCount: Long,
    val pendingContributionCount: Long,
    val pendingCleanupRequired: Boolean,
    val resultOpenedAt: LocalDateTime?,
    val canExtend: Boolean,
    val giftChosen: Boolean,
    val contributions: List<FundingResultContributionResponse>,
) {
    companion object {
        private val GIFT_CHOSEN_STATUSES = setOf(FundingStatus.WAITING_GIFT_VERIFICATION, FundingStatus.COMPLETED)

        fun from(
            funding: Funding,
            summary: FundingContributionSummary,
            achievementPercentage: Int,
            messageCount: Long,
            canExtend: Boolean,
            contributions: List<FundingResultContributionResponse>,
        ): FundingResultResponse =
            FundingResultResponse(
                fundingId = funding.id,
                productName = funding.product.name,
                productImageUrl = funding.product.imageUrl,
                productUrl = funding.product.url,
                productChanged = funding.productChangedAt != null,
                targetAmount = funding.targetAmount,
                confirmedAmount = summary.confirmedAmount,
                achievementPercentage = achievementPercentage,
                targetReached = summary.confirmedAmount >= funding.targetAmount,
                participantCount = summary.participantCount,
                messageCount = messageCount,
                pendingContributionCount = summary.pendingCount,
                pendingCleanupRequired = funding.resultOpenedAt == null && summary.pendingCount > 0,
                resultOpenedAt = funding.resultOpenedAt,
                canExtend = canExtend,
                giftChosen = funding.status in GIFT_CHOSEN_STATUSES,
                contributions = contributions,
            )
    }
}

// 감사카드를 보탬 단위로 보내므로 사람이 아니라 보탬 한 건마다 한 줄로 내려준다. 금액은 노출하지 않는다
data class FundingResultContributionResponse(
    val contributionId: Long,
    val name: String,
    val profileImageUrl: String?,
    val status: ContributionStatus,
    val participatedAt: LocalDateTime,
    val hasMessage: Boolean,
    val hasPhoto: Boolean,
    val hasVoice: Boolean,
    val thankCardSent: Boolean,
)
