package com.botem.domain.funding.dto.response

import com.botem.domain.funding.entity.GiftVerification
import com.botem.domain.funding.entity.GiftVerificationStatus
import java.time.LocalDateTime

data class GiftVerificationResponse(
    val giftVerificationId: Long,
    val fundingId: Long,
    val status: GiftVerificationStatus,
    val imageUrl: String?,
    val thankYouMessage: String?,
    val purchasedThroughBotemLink: Boolean,
    val replacementProductName: String?,
    val completedAt: LocalDateTime?,
) {
    companion object {
        fun from(verification: GiftVerification): GiftVerificationResponse =
            GiftVerificationResponse(
                giftVerificationId = verification.id,
                fundingId = verification.fundingId,
                status = verification.status,
                imageUrl = verification.imageUrl,
                thankYouMessage = verification.thankYouMessage,
                purchasedThroughBotemLink = verification.purchasedThroughBotemLink,
                replacementProductName = verification.replacementProductName,
                completedAt = verification.completedAt,
            )
    }
}
