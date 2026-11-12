package com.botem.domain.friend.dto.request

import jakarta.validation.constraints.NotBlank

data class AddFriendRequest(
    // 친구 위시 프로필(초대 링크) 주소의 @ 뒤 값
    @field:NotBlank
    val wishProfileKey: String,
)
