package com.botem.domain.home.dto.response

data class HomeNoFundingResponse(
    val state: HomeNoFundingState,
    // 화면 표시 이름. 마이페이지에서 수정하는 이름(name)을 쓰고, 온보딩 전이라 없으면 카카오 닉네임
    val nickname: String,
    val birthdayDday: Long?,
    val wishes: HomeWishPageResponse,
)
