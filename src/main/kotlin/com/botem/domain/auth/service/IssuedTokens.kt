package com.botem.domain.auth.service

data class IssuedTokens(
    val accessToken: String,
    val refreshToken: String,
)
