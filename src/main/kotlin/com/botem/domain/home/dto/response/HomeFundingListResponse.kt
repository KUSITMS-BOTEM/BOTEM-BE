package com.botem.domain.home.dto.response

data class HomeFundingListResponse(
    val fundingCards: List<HomeFundingCardResponse>,
    val activeFundingCount: Int,
    val canOpenFunding: Boolean,
)
