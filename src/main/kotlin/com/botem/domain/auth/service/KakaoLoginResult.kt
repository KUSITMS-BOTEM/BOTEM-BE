package com.botem.domain.auth.service

data class KakaoLoginResult(
    val tokens: IssuedTokens,
    val isNewUser: Boolean,
)
