package com.botem.domain.funding.dto.response

import com.botem.domain.funding.entity.FundingType
import java.time.LocalDateTime

data class ClosedFundingListResponse(
    val fundings: List<ClosedFundingResponse>,
)

data class ClosedFundingResponse(
    val fundingId: Long,
    val shareKey: String,
    val type: FundingType,
    val productName: String,
    val productImageUrl: String,
    val deadline: LocalDateTime,
    val targetReached: Boolean,
    val participantCount: Long,
    val recipientName: String?,
    val giftVerificationStatus: ClosedFundingGiftVerificationStatus?,
)

enum class ClosedFundingGiftVerificationStatus {
    REQUIRED,
    COMPLETED,
}
