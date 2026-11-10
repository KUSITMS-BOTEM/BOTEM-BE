package com.botem.domain.home.dto.response

import com.botem.domain.funding.repository.projection.PendingGiftVerificationTarget
import java.time.LocalDateTime

data class GiftVerificationTargetResponse(
    val giftVerificationId: Long,
    val fundingId: Long,
    val productName: String,
    val productImageUrl: String,
    val fundingDeadline: LocalDateTime,
    val participantCount: Long,
    val replacementProductName: String?,
) {
    companion object {
        fun from(target: PendingGiftVerificationTarget): GiftVerificationTargetResponse =
            GiftVerificationTargetResponse(
                giftVerificationId = target.giftVerificationId,
                fundingId = target.fundingId,
                productName = target.productName,
                productImageUrl = target.productImageUrl,
                fundingDeadline = target.fundingDeadline,
                participantCount = target.participantCount,
                replacementProductName = target.replacementProductName,
            )
    }
}
