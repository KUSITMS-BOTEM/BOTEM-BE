package com.botem.domain.user.service

/** 일반 펀딩과 서프라이즈 펀딩을 포함해 사용자가 개설한 진행 중 펀딩이 있는지 확인한다. */
fun interface ActiveFundingChecker {

    fun existsByUserId(userId: Long): Boolean
}
