package com.botem.domain.funding.dto.request

import jakarta.validation.constraints.NotBlank

data class SendSurpriseThankCardRequest(
    @field:NotBlank
    val message: String,
)
