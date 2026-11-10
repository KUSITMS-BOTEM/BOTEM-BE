package com.botem.domain.home.dto.response

import com.botem.domain.funding.repository.projection.RevealedSurpriseCardProjection
import java.time.LocalDateTime

data class SurpriseRevealCardResponse(
    val fundingId: Long,
    val productImageUrl: String,
    val productName: String,
    val organizerName: String,
    val participantCount: Long,
    val revealAt: LocalDateTime,
) {
    companion object {
        fun from(card: RevealedSurpriseCardProjection): SurpriseRevealCardResponse =
            SurpriseRevealCardResponse(
                fundingId = card.fundingId,
                productImageUrl = card.productImageUrl,
                productName = card.productName,
                organizerName = card.organizerName,
                participantCount = card.participantCount,
                revealAt = card.revealAt,
            )
    }
}
