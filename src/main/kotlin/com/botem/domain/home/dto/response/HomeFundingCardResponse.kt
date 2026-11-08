package com.botem.domain.home.dto.response

import com.botem.domain.funding.entity.FundingType

data class HomeFundingCardResponse(
    val fundingId: Long,
    val cardType: HomeFundingCardType,
    val fundingType: FundingType,
    val productName: String,
    val productImageUrl: String,
    val remainingDays: Long?,
    val confirmedPercentage: Int,
    val pendingPercentage: Int,
    val pendingContributionCount: Long,
)
