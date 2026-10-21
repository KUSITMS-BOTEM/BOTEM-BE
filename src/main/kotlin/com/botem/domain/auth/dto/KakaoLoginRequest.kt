package com.botem.domain.auth.dto

import jakarta.validation.constraints.NotBlank

data class KakaoLoginRequest(
    @NotBlank
    val code: String,
)
