package com.botem.domain.user.dto.response

import com.botem.domain.user.entity.User
import java.time.LocalDate

data class MyProfileResponse(
    val nickname: String,
    val profileImageUrl: String?,
    val birthday: LocalDate?,
    val kakaoConnected: Boolean,
) {
    companion object {
        fun from(user: User): MyProfileResponse =
            MyProfileResponse(
                nickname = user.nickname,
                profileImageUrl = user.profileImageUrl,
                birthday = user.birthday,
                kakaoConnected = true,
            )
    }
}
