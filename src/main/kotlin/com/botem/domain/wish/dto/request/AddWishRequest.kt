package com.botem.domain.wish.dto.request

import jakarta.validation.constraints.Positive

data class AddWishRequest(
    @field:Positive
    val recommendedProductId: Long,
)
