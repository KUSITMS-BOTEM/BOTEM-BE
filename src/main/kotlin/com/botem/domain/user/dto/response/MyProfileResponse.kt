package com.botem.domain.user.dto.response

import com.botem.domain.user.entity.User
import com.botem.global.entity.auditTimeToKorea
import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDate
import java.time.MonthDay

data class MyProfileResponse(
    val nickname: String,
    val name: String?,
    val profileImageUrl: String?,
    @get:JsonFormat(pattern = "MM-dd")
    val birthday: MonthDay?,
    val wishProfileUrl: String,
    val kakaoConnected: Boolean,
    val email: String?,
    @get:JsonFormat(pattern = "yyyy-MM-dd")
    val joinedAt: LocalDate,
) {
    companion object {
        fun from(
            user: User,
            wishProfileBaseUrl: String,
            wishProfileKey: String,
        ): MyProfileResponse =
            MyProfileResponse(
                nickname = user.nickname,
                name = user.name,
                profileImageUrl = user.profileImageUrl,
                birthday = user.birthday,
                wishProfileUrl = "${wishProfileBaseUrl.trimEnd('/')}/@$wishProfileKey",
                kakaoConnected = user.kakaoConnected,
                email = user.email,
                // created_at은 UTC로 저장되므로 한국 날짜로 바꿔 가입일 하루 밀림을 막음
                joinedAt = user.createdAt.auditTimeToKorea().toLocalDate(),
            )
    }
}
