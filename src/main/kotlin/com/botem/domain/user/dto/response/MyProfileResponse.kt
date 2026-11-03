package com.botem.domain.user.dto.response

import com.botem.domain.user.entity.User
import java.time.LocalDate

data class MyProfileResponse(
    val nickname: String,
    val profileImageUrl: String?,
    val birthday: LocalDate?,
    val wishProfileUrl: String,
    val kakaoConnected: Boolean,
) {
    companion object {
        fun from(user: User, wishProfileBaseUrl: String): MyProfileResponse =
            MyProfileResponse(
                nickname = user.nickname,
                profileImageUrl = user.profileImageUrl,
                birthday = user.birthday,
                wishProfileUrl = "${wishProfileBaseUrl.trimEnd('/')}/@${user.wishProfileKey}",
                kakaoConnected = true,
            )
    }
}
