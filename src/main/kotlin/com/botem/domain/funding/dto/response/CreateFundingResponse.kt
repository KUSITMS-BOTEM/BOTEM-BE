package com.botem.domain.funding.dto.response

import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType

data class CreateFundingResponse(
    val fundingId: Long,
    val type: FundingType,
    val status: FundingStatus,
    val shareUrl: String,
) {
    companion object {
        fun from(funding: Funding, shareUrl: String): CreateFundingResponse =
            CreateFundingResponse(
                fundingId = funding.id,
                type = funding.type,
                status = funding.status,
                shareUrl = shareUrl,
            )
    }
}
