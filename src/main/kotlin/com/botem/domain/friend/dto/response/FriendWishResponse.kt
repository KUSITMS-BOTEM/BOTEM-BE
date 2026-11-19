package com.botem.domain.friend.dto.response

data class FriendWishResponse(
    val wishId: Long,
    val productName: String,
    val productImageUrl: String,
    val price: Long,
    val productUrl: String,
    val sellerName: String?,
    // 이 친구에게 이미 진행 중인 서프라이즈가 있으면 그 펀딩 ID ("같이 보태기"), 없으면 null ("친구 몰래 서프라이즈 열기")
    val activeSurpriseFundingId: Long?,
)
