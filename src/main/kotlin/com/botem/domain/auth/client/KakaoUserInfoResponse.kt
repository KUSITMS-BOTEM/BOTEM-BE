package com.botem.domain.auth.client

import com.fasterxml.jackson.annotation.JsonProperty

data class KakaoUserInfoResponse(
    val id: Long,

    @JsonProperty("kakao_account")
    val kakaoAccount: KakaoAccount?,
) {
    data class KakaoAccount(
        val profile: Profile?,

        val email: String? = null,

        @JsonProperty("is_email_valid")
        val isEmailValid: Boolean? = null,

        @JsonProperty("is_email_verified")
        val isEmailVerified: Boolean? = null,
    ) {
        // 만료되었거나 인증되지 않은 이메일은 본인 이메일이라고 볼 수 없어 저장하지 않음
        fun verifiedEmail(): String? =
            email?.takeIf { isEmailValid == true && isEmailVerified == true }
    }

    data class Profile(
        val nickname: String?,

        @JsonProperty("profile_image_url")
        val profileImageUrl: String?,
    )
}
