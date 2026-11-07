package com.botem.domain.wish.dto.response

import com.botem.domain.wish.entity.Wish

data class WishResponse(
    val id: Long,
    val productName: String,
    val productImageUrl: String,
    val price: Long,
    val productUrl: String,
) {
    companion object {
        fun from(wish: Wish): WishResponse =
            WishResponse(
                id = wish.id,
                productName = wish.productName,
                productImageUrl = wish.productImageUrl,
                price = wish.price,
                productUrl = wish.productUrl,
            )
    }
}
