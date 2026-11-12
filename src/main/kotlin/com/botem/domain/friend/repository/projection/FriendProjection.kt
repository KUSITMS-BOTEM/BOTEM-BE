package com.botem.domain.friend.repository.projection

import java.time.MonthDay

data class FriendProjection(
    val userId: Long,
    val name: String?,
    val nickname: String,
    val profileImageUrl: String?,
    val birthday: MonthDay?,
    val wishCount: Long,
)
