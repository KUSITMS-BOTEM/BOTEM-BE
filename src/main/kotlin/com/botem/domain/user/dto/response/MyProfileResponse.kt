package com.botem.domain.user.dto.response

import com.botem.domain.user.entity.User
import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId

data class MyProfileResponse(
    val nickname: String,
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
                profileImageUrl = user.profileImageUrl,
                birthday = user.birthday,
                wishProfileUrl = "${wishProfileBaseUrl.trimEnd('/')}/@$wishProfileKey",
                kakaoConnected = user.kakaoConnected,
                email = user.email,
                // created_at은 서버 시간대로 저장되므로(운영 컨테이너는 UTC) 한국 날짜로 바꿔 가입일 하루 밀림을 막음
                joinedAt = user.createdAt.atZone(ZoneId.systemDefault()).withZoneSameInstant(KOREA_ZONE).toLocalDate(),
            )

        private val KOREA_ZONE = ZoneId.of("Asia/Seoul")
    }
}
