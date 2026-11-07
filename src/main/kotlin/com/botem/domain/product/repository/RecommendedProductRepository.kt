package com.botem.domain.product.repository

import com.botem.domain.product.entity.ProductCategory
import com.botem.domain.product.entity.RecommendedProduct
import com.botem.domain.product.entity.RecommendedProductStatus
import org.springframework.data.jpa.repository.JpaRepository

interface RecommendedProductRepository : JpaRepository<RecommendedProduct, Long> {

    fun findAllByStatusOrderByDisplayOrderAscIdAsc(status: RecommendedProductStatus): List<RecommendedProduct>

    fun findAllByStatusAndCategoryOrderByDisplayOrderAscIdAsc(
        status: RecommendedProductStatus,
        category: ProductCategory,
    ): List<RecommendedProduct>
}
