package com.botem.domain.funding.dto.response

import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.repository.projection.FundingContributionSummary
import com.botem.domain.funding.repository.projection.RollingPaperMessageProjection
import java.time.LocalDateTime

data class SurpriseRevealDetailResponse(
    val fundingId: Long,
    val recipientName: String,
    val organizerName: String,
    val productName: String,
    val productImageUrl: String,
    val productUrl: String,
    val productPrice: Long,
    val productChanged: Boolean,
    val confirmedAmount: Long,
    val targetAmount: Long,
    // 홈 카드·펀딩 상세와 같은 기준(소수점 버림)의 확정 금액 달성률
    val confirmedPercentage: Int,
    val deadline: LocalDateTime,
    val participantCount: Long,
    val messageCount: Int,
    val revealAt: LocalDateTime,
    val openedAt: LocalDateTime?,
    val giftChosen: Boolean,
    val messages: List<RollingPaperMessageResponse>,
) {
    companion object {
        private val GIFT_CHOSEN_STATUSES = setOf(FundingStatus.WAITING_GIFT_VERIFICATION, FundingStatus.COMPLETED)

        fun from(
            funding: Funding,
            recipientName: String,
            organizerName: String,
            summary: FundingContributionSummary,
            confirmedPercentage: Int,
            messages: List<RollingPaperMessageProjection>,
        ): SurpriseRevealDetailResponse =
            SurpriseRevealDetailResponse(
                fundingId = funding.id,
                recipientName = recipientName,
                organizerName = organizerName,
                productName = funding.product.name,
                productImageUrl = funding.product.imageUrl,
                productUrl = funding.product.url,
                productPrice = funding.product.price,
                productChanged = funding.productChangedAt != null,
                confirmedAmount = summary.confirmedAmount,
                targetAmount = funding.targetAmount,
                confirmedPercentage = confirmedPercentage,
                deadline = funding.deadline,
                participantCount = summary.participantCount,
                messageCount = messages.size,
                revealAt = checkNotNull(funding.revealAt),
                openedAt = funding.surpriseOpenedAt,
                giftChosen = funding.status in GIFT_CHOSEN_STATUSES,
                messages = messages.map(RollingPaperMessageResponse::from),
            )
    }
}
