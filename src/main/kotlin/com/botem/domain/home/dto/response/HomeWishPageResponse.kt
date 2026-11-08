package com.botem.domain.home.dto.response

import com.botem.domain.wish.entity.Wish
import org.springframework.data.domain.Page

data class HomeWishPageResponse(
    val content: List<HomeWishCardResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val hasNext: Boolean,
) {
    companion object {
        fun from(wishes: Page<Wish>): HomeWishPageResponse =
            HomeWishPageResponse(
                content = wishes.content.map(HomeWishCardResponse::from),
                page = wishes.number,
                size = wishes.size,
                totalElements = wishes.totalElements,
                hasNext = wishes.hasNext(),
            )
    }
}
