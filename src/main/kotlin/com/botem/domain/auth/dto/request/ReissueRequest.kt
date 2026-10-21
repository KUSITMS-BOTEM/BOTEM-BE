package com.botem.domain.auth.dto.request

import jakarta.validation.constraints.NotBlank

data class ReissueRequest(
    @NotBlank
    val refreshToken: String,
)
