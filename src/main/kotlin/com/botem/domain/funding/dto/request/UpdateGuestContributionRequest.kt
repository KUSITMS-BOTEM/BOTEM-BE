package com.botem.domain.funding.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UpdateGuestContributionRequest(
    @field:NotBlank
    @field:Size(max = 50)
    val participantName: String,

    @field:NotBlank
    @field:Size(max = 460)
    val message: String,

    @field:NotBlank
    @field:Size(max = 50)
    val paperTemplateCode: String,
)
