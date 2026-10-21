package com.botem.domain.auth.dto.response

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
)
