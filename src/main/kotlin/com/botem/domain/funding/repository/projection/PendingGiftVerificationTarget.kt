package com.botem.domain.funding.repository.projection

import java.time.LocalDateTime

data class PendingGiftVerificationTarget(
    val giftVerificationId: Long,
    val fundingId: Long,
    val productName: String,
    val productImageUrl: String,
    val fundingDeadline: LocalDateTime,
    val participantCount: Long,
    val replacementProductName: String?,
)
