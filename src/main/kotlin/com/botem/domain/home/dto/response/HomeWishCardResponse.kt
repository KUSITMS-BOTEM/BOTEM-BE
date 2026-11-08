package com.botem.domain.home.dto.response

import com.botem.domain.wish.entity.Wish

data class HomeWishCardResponse(
    val wishId: Long,
    val productName: String,
    val productImageUrl: String,
    val price: Long,
    val productUrl: String,
) {
    companion object {
        fun from(wish: Wish): HomeWishCardResponse =
            HomeWishCardResponse(
                wishId = wish.id,
                productName = wish.productName,
                productImageUrl = wish.productImageUrl,
                price = wish.price,
                productUrl = wish.productUrl,
            )
    }
}
