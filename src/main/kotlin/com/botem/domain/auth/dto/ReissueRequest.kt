package com.botem.domain.auth.dto

import jakarta.validation.constraints.NotBlank

data class ReissueRequest(
    @NotBlank
    val refreshToken: String,
)
