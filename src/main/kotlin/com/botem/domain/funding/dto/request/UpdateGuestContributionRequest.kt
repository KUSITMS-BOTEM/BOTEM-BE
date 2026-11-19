package com.botem.domain.funding.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

data class UpdateGuestContributionRequest(
    @field:NotBlank
    @field:Size(max = 50)
    val participantName: String,

    @field:Size(max = 460)
    val message: String? = null,

    @field:NotBlank
    @field:Size(max = 50)
    val paperTemplateCode: String,

    @field:Size(max = 50)
    val decorationCode: String? = null,

    @field:Size(max = 2048)
    val photoUrl: String? = null,

    @field:Size(max = 2048)
    val voiceUrl: String? = null,

    @field:Positive
    val voiceDurationSeconds: Int? = null,
)
