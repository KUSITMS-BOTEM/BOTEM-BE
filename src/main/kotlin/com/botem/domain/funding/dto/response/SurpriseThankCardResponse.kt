package com.botem.domain.funding.dto.response

import com.botem.domain.funding.entity.SurpriseThankCard
import java.time.LocalDateTime

data class SurpriseThankCardResponse(
    val thankCardId: Long,
    val fundingId: Long,
    val message: String,
    val sentAt: LocalDateTime,
) {
    companion object {
        fun from(thankCard: SurpriseThankCard): SurpriseThankCardResponse =
            SurpriseThankCardResponse(
                thankCardId = thankCard.id,
                fundingId = thankCard.fundingId,
                message = thankCard.message,
                sentAt = thankCard.sentAt,
            )
    }
}
