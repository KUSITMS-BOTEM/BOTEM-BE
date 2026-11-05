package com.botem.domain.auth.dto.response

data class KakaoLoginResponse(
    val accessToken: String,
    val isNewUser: Boolean,
)
