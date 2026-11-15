package com.botem.domain.funding.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class CompleteGiftVerificationRequest(
    @field:NotBlank
    val imageUrl: String,

    val thankYouMessage: String?,

    @field:NotNull
    val purchasedThroughBotemLink: Boolean,

    @field:Size(max = 100)
    val replacementProductName: String?,
)
