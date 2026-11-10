package com.botem.domain.wish.dto.response

import com.botem.domain.wish.entity.Wish
import com.botem.domain.wish.entity.WishStatus

data class WishResponse(
    val id: Long,
    val productName: String,
    val productImageUrl: String,
    val price: Long,
    val productUrl: String,
    val sellerName: String?,
    val status: WishStatus,
    // 끝나지 않은 펀딩에 연결된 위시면 그 펀딩 ID. 상세의 "펀딩 보기" 이동에 사용
    val fundingId: Long?,
) {
    companion object {
        fun from(
            wish: Wish,
            status: WishStatus = WishStatus.AVAILABLE,
            fundingId: Long? = null,
        ): WishResponse =
            WishResponse(
                id = wish.id,
                productName = wish.productName,
                productImageUrl = wish.productImageUrl,
                price = wish.price,
                productUrl = wish.productUrl,
                sellerName = wish.sellerName,
                status = status,
                fundingId = fundingId,
            )
    }
}
