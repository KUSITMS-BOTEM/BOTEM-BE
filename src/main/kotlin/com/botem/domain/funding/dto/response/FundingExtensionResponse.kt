package com.botem.domain.funding.dto.response

import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingStatus
import java.time.LocalDateTime

data class FundingExtensionResponse(
    val fundingId: Long,
    val status: FundingStatus,
    val deadline: LocalDateTime,
    val extensionCount: Int,
    val extendedAt: LocalDateTime,
) {
    companion object {
        fun from(funding: Funding): FundingExtensionResponse =
            FundingExtensionResponse(
                fundingId = funding.id,
                status = funding.status,
                deadline = funding.deadline,
                extensionCount = funding.extensionCount,
                extendedAt = checkNotNull(funding.extendedAt),
            )
    }
}
