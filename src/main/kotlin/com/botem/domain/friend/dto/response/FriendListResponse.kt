package com.botem.domain.friend.dto.response

data class FriendListResponse(
    val totalCount: Int,
    val upcomingBirthdayFriends: List<FriendResponse>,
    val friends: List<FriendResponse>,
)
