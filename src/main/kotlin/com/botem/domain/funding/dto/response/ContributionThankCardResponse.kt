package com.botem.domain.funding.dto.response

import com.botem.domain.funding.entity.ContributionThankCard
import com.botem.domain.funding.entity.ThankCardColor
import java.time.LocalDateTime

data class ContributionThankCardResponse(
    val thankCardId: Long,
    val contributionId: Long,
    val message: String,
    val paperColor: ThankCardColor,
    val sentAt: LocalDateTime,
    val shareUrl: String,
) {
    companion object {
        fun from(
            thankCard: ContributionThankCard,
            shareUrl: String,
        ): ContributionThankCardResponse =
            ContributionThankCardResponse(
                thankCardId = thankCard.id,
                contributionId = thankCard.contributionId,
                message = thankCard.message,
                paperColor = thankCard.paperColor,
                sentAt = thankCard.sentAt,
                shareUrl = shareUrl,
            )
    }
}
