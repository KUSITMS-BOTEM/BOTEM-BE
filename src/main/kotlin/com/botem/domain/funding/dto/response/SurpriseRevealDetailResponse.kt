package com.botem.domain.funding.dto.response

import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.repository.projection.FundingContributionSummary
import com.botem.domain.funding.repository.projection.RollingPaperMessageProjection
import java.time.LocalDateTime

data class SurpriseRevealDetailResponse(
    val fundingId: Long,
    val recipientName: String,
    val organizerName: String,
    val productName: String,
    val productImageUrl: String,
    val confirmedAmount: Long,
    val participantCount: Long,
    val messageCount: Int,
    val revealAt: LocalDateTime,
    val openedAt: LocalDateTime?,
    val messages: List<RollingPaperMessageResponse>,
) {
    companion object {
        fun from(
            funding: Funding,
            recipientName: String,
            organizerName: String,
            summary: FundingContributionSummary,
            messages: List<RollingPaperMessageProjection>,
        ): SurpriseRevealDetailResponse =
            SurpriseRevealDetailResponse(
                fundingId = funding.id,
                recipientName = recipientName,
                organizerName = organizerName,
                productName = funding.product.name,
                productImageUrl = funding.product.imageUrl,
                confirmedAmount = summary.confirmedAmount,
                participantCount = summary.participantCount,
                messageCount = messages.size,
                revealAt = checkNotNull(funding.revealAt),
                openedAt = funding.surpriseOpenedAt,
                messages = messages.map(RollingPaperMessageResponse::from),
            )
    }
}
