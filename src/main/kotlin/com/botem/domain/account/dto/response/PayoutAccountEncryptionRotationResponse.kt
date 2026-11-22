package com.botem.domain.account.dto.response

data class PayoutAccountEncryptionRotationResponse(
    val processedCount: Int,
    val rotatedCount: Int,
    val nextAfterAccountId: Long?,
)
