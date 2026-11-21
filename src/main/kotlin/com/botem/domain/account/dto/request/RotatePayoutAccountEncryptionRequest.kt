package com.botem.domain.account.dto.request

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.PositiveOrZero

data class RotatePayoutAccountEncryptionRequest(
    @field:PositiveOrZero
    val afterAccountId: Long? = null,

    @field:Min(1)
    @field:Max(500)
    val batchSize: Int = 100,
)
