package com.botem.domain.funding.dto.response

import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import java.time.LocalDateTime

data class FundingEarlyCloseResponse(
    val fundingId: Long,
    val type: FundingType,
    val status: FundingStatus,
    val deadline: LocalDateTime,
    val revealAt: LocalDateTime?,
    val earlyClosedAt: LocalDateTime,
) {
    companion object {
        fun from(funding: Funding): FundingEarlyCloseResponse =
            FundingEarlyCloseResponse(
                fundingId = funding.id,
                type = funding.type,
                status = funding.status,
                deadline = funding.deadline,
                revealAt = funding.revealAt,
                earlyClosedAt = checkNotNull(funding.earlyClosedAt),
            )
    }
}
