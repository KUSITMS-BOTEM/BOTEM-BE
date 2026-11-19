package com.botem.domain.product.dto.response

import com.botem.domain.product.entity.ProductCategory
import com.botem.domain.product.entity.RecommendedProduct
import com.botem.domain.product.entity.RecommendedProductStatus

data class RecommendedProductResponse(
    val id: Long,
    val name: String,
    val imageUrl: String,
    val price: Long,
    val sellerName: String,
    val category: ProductCategory,
    val status: RecommendedProductStatus,
    // 내 위시에 담은 상품이면 그 위시 ID
    val wishId: Long?,
    // 그 위시로 내가 연 펀딩이 끝나지 않았으면 그 펀딩 ID
    val fundingId: Long?,
) {
    companion object {
        fun from(
            product: RecommendedProduct,
            wishId: Long? = null,
            fundingId: Long? = null,
        ): RecommendedProductResponse =
            RecommendedProductResponse(
                id = product.id,
                name = product.name,
                imageUrl = product.imageUrl,
                price = product.price,
                sellerName = product.sellerName,
                category = product.category,
                status = product.status,
                wishId = wishId,
                fundingId = fundingId,
            )
    }
}
