package com.botem.domain.home.dto.response

data class HomeNoFundingResponse(
    val state: HomeNoFundingState,
    val nickname: String,
    val birthdayDday: Long?,
    val wishes: List<HomeWishCardResponse>,
)
