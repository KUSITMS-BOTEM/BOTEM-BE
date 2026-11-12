package com.botem.domain.friend.dto.response

import com.botem.domain.friend.repository.projection.FriendProjection
import com.botem.domain.user.entity.daysUntilNextBirthday
import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDate
import java.time.MonthDay

data class FriendResponse(
    val userId: Long,
    val name: String,
    val profileImageUrl: String?,
    @get:JsonFormat(pattern = "MM-dd")
    val birthday: MonthDay?,
    val birthdayDday: Long?,
    val wishCount: Long,
) {
    companion object {
        fun from(friend: FriendProjection, today: LocalDate): FriendResponse =
            FriendResponse(
                userId = friend.userId,
                // 홈·서프라이즈 카드와 같이 이름을 우선하고 온보딩 전이면 카카오 닉네임
                name = friend.name ?: friend.nickname,
                profileImageUrl = friend.profileImageUrl,
                birthday = friend.birthday,
                birthdayDday = friend.birthday?.daysUntilNextBirthday(today),
                wishCount = friend.wishCount,
            )
    }
}
