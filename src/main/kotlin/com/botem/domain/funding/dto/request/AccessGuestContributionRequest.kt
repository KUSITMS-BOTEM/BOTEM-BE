package com.botem.domain.funding.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class AccessGuestContributionRequest(
    @field:NotBlank
    @field:Size(max = 50)
    val participantName: String,

    @field:Pattern(regexp = "\\d{4}", message = "숫자 4자리여야 합니다")
    val guestPassword: String,
)
