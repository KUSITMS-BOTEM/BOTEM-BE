package com.botem.domain.product.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RequestProductRequest(
    @field:NotBlank
    @field:Size(max = 2048)
    val productUrl: String,

    val productName: String? = null,
)
