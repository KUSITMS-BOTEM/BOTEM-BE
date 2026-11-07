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
) {
    companion object {
        fun from(product: RecommendedProduct): RecommendedProductResponse =
            RecommendedProductResponse(
                id = product.id,
                name = product.name,
                imageUrl = product.imageUrl,
                price = product.price,
                sellerName = product.sellerName,
                category = product.category,
                status = product.status,
            )
    }
}
