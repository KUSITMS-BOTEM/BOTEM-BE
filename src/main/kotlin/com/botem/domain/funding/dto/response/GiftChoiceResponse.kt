package com.botem.domain.funding.dto.response

import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.GiftVerification

data class GiftChoiceResponse(
    val fundingId: Long,
    val giftVerificationId: Long?,
    val productName: String,
    val productImageUrl: String,
    val productUrl: String,
) {
    companion object {
        fun of(funding: Funding, verification: GiftVerification?): GiftChoiceResponse =
            GiftChoiceResponse(
                fundingId = funding.id,
                giftVerificationId = verification?.id,
                productName = funding.product.name,
                productImageUrl = funding.product.imageUrl,
                productUrl = funding.product.url,
            )
    }
}
