package com.botem.domain.funding.dto.request

import jakarta.validation.constraints.Positive

data class ChangeFundingProductRequest(
    @field:Positive(message = "0보다 커야 합니다")
    val wishId: Long,
)
