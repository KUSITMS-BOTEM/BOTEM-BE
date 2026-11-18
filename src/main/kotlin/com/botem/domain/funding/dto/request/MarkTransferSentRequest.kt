package com.botem.domain.funding.dto.request

import jakarta.validation.constraints.Pattern

data class MarkTransferSentRequest(
    @field:Pattern(regexp = "\\d{4}")
    val guestPassword: String? = null,
)
