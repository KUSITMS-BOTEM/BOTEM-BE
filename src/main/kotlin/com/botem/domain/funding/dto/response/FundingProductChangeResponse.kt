package com.botem.domain.funding.dto.response

import com.botem.domain.funding.entity.Funding
import java.time.LocalDateTime

data class FundingProductChangeResponse(
    val fundingId: Long,
    val product: FundingDetailProductResponse,
    val productChangedAt: LocalDateTime?,
) {
    companion object {
        fun from(funding: Funding): FundingProductChangeResponse =
            FundingProductChangeResponse(
                fundingId = funding.id,
                product = FundingDetailProductResponse.from(funding.product),
                productChangedAt = funding.productChangedAt,
            )
    }
}
