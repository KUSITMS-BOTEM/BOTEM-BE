package com.botem.domain.funding.service

import java.math.BigDecimal
import java.math.RoundingMode

private val ONE_HUNDRED = BigDecimal.valueOf(100L)
private val MAX_INT = BigDecimal.valueOf(Int.MAX_VALUE.toLong())

// 홈 카드, 펀딩 상세, 관리 화면이 같은 기준으로 달성률을 계산하도록 공유한다 (소수점 버림)
fun progressPercentage(amount: Long, targetAmount: Long): Int {
    if (amount <= 0L || targetAmount <= 0L) {
        return 0
    }

    return BigDecimal.valueOf(amount)
        .multiply(ONE_HUNDRED)
        .divide(BigDecimal.valueOf(targetAmount), 0, RoundingMode.DOWN)
        .min(MAX_INT)
        .toInt()
}
