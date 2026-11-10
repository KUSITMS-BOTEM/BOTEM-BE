package com.botem.domain.funding.repository.projection

import java.time.LocalDateTime

data class RevealedSurpriseCardProjection(
    val fundingId: Long,
    val productImageUrl: String,
    val productName: String,
    val organizerName: String,
    val participantCount: Long,
    val revealAt: LocalDateTime,
)
