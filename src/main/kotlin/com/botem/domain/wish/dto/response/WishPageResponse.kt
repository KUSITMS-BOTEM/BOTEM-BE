package com.botem.domain.wish.dto.response

import org.springframework.data.domain.Page

data class WishPageResponse(
    val content: List<WishResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val hasNext: Boolean,
) {
    companion object {
        fun of(page: Page<*>, content: List<WishResponse>): WishPageResponse =
            WishPageResponse(
                content = content,
                page = page.number,
                size = page.size,
                totalElements = page.totalElements,
                hasNext = page.hasNext(),
            )
    }
}
