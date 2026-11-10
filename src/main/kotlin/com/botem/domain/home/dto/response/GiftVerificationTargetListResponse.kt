package com.botem.domain.home.dto.response

data class GiftVerificationTargetListResponse(
    val pendingCount: Int,
    val targets: List<GiftVerificationTargetResponse>,
)
