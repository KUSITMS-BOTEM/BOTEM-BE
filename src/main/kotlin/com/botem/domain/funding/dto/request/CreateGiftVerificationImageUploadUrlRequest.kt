package com.botem.domain.funding.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class CreateGiftVerificationImageUploadUrlRequest(
    @field:NotBlank
    val contentType: String,

    @field:NotNull
    val contentLength: Long,
)
