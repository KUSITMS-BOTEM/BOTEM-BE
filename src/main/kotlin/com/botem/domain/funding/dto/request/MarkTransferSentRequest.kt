package com.botem.domain.funding.dto.request

import jakarta.validation.constraints.Pattern

data class MarkTransferSentRequest(
    @field:Pattern(regexp = "\\d{4}", message = "숫자 4자리여야 합니다")
    val guestPassword: String? = null,
)
