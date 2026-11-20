package com.botem.domain.funding.dto.request

import com.botem.domain.funding.entity.ThankCardColor

data class SendContributionThankCardRequest(
    val message: String,
    // 생략하면 화면 기본값인 LIME
    val paperColor: ThankCardColor = ThankCardColor.LIME,
)
