package com.botem.domain.funding.dto.response

import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.repository.projection.FundingContributionSummary
import java.time.LocalDateTime

// 주최자 마감 · 선물 준비(SUR-PG-007)와 준비 완료 · 공유(SUR-PG-008) 화면이 함께 쓴다
data class SurpriseGiftPreparationResponse(
    val fundingId: Long,
    val recipientName: String?,
    val productName: String,
    val productImageUrl: String,
    val productUrl: String,
    val targetAmount: Long,
    val confirmedAmount: Long,
    val achievementPercentage: Int,
    val targetReached: Boolean,
    val participantCount: Long,
    val messageCount: Long,
    val pendingContributionCount: Long,
    val pendingCleanupRequired: Boolean,
    val fundingStartedAt: LocalDateTime,
    val deadline: LocalDateTime,
    val earlyClosedAt: LocalDateTime?,
    val revealAt: LocalDateTime?,
    val giftPreparedAt: LocalDateTime?,
) {
    companion object {
        fun from(
            funding: Funding,
            recipientName: String?,
            summary: FundingContributionSummary,
            achievementPercentage: Int,
            messageCount: Long,
        ): SurpriseGiftPreparationResponse =
            SurpriseGiftPreparationResponse(
                fundingId = funding.id,
                recipientName = recipientName,
                productName = funding.product.name,
                productImageUrl = funding.product.imageUrl,
                productUrl = funding.product.url,
                targetAmount = funding.targetAmount,
                confirmedAmount = summary.confirmedAmount,
                achievementPercentage = achievementPercentage,
                targetReached = summary.confirmedAmount >= funding.targetAmount,
                participantCount = summary.participantCount,
                messageCount = messageCount,
                pendingContributionCount = summary.pendingCount,
                pendingCleanupRequired = !funding.isSurpriseGiftPrepared() && summary.pendingCount > 0,
                fundingStartedAt = funding.createdAt,
                deadline = funding.deadline,
                earlyClosedAt = funding.earlyClosedAt,
                revealAt = funding.revealAt,
                giftPreparedAt = funding.giftPreparedAt,
            )
    }
}
