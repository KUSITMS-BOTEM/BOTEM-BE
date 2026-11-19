package com.botem.domain.funding.repository.projection

import com.botem.domain.funding.entity.FundingType

// 회원별로 프로필에 보이는 진행 중 펀딩 종류. 생일은 개설자, 서프라이즈는 받는 사람 기준이다
data class ProfileFundingTypeProjection(
    val userId: Long,
    val type: FundingType,
)
