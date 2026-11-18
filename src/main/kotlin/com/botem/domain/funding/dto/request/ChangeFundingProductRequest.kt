package com.botem.domain.funding.dto.request

import jakarta.validation.constraints.Positive

data class ChangeFundingProductRequest(
    @field:Positive
    val wishId: Long,
)
