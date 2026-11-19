package com.botem.domain.funding.dto.response

import com.botem.domain.funding.entity.ContributionThankCard
import java.time.LocalDateTime

data class ContributionThankCardResponse(
    val thankCardId: Long,
    val contributionId: Long,
    val message: String,
    val sentAt: LocalDateTime,
) {
    companion object {
        fun from(thankCard: ContributionThankCard): ContributionThankCardResponse =
            ContributionThankCardResponse(
                thankCardId = thankCard.id,
                contributionId = thankCard.contributionId,
                message = thankCard.message,
                sentAt = thankCard.sentAt,
            )
    }
}
