package com.botem.domain.funding.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

data class CreateContributionRequest(
    @field:NotBlank
    @field:Size(max = 64)
    val requestKey: String,

    @field:Size(max = 50)
    val participantName: String? = null,

    @field:Positive
    val amount: Long,

    @field:NotBlank
    @field:Size(max = 460)
    val message: String,

    @field:NotBlank
    @field:Size(max = 50)
    val paperTemplateCode: String,

    @field:Pattern(regexp = "\\d{4}", message = "숫자 4자리여야 합니다")
    val guestPassword: String? = null,
)
