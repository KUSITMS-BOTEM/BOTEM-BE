package com.botem.domain.product.service

import com.botem.domain.product.dto.response.RecommendedProductResponse
import com.botem.domain.product.entity.ProductCategory
import com.botem.domain.product.entity.RecommendedProduct
import com.botem.domain.product.entity.RecommendedProductStatus
import com.botem.domain.product.exception.RecommendedProductErrorCode
import com.botem.domain.product.repository.RecommendedProductRepository
import com.botem.global.exception.BusinessException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RecommendedProductService(
    private val recommendedProductRepository: RecommendedProductRepository,
) {

    // 판매 종료 상품은 목록에서 숨기고, 공유 링크나 담긴 위시로 들어온 상세 조회에서만 상태와 함께 노출
    @Transactional(readOnly = true)
    fun getRecommendedProducts(category: ProductCategory?): List<RecommendedProductResponse> {
        val products = if (category == null) {
            recommendedProductRepository.findAllByStatusOrderByDisplayOrderAscIdAsc(RecommendedProductStatus.ON_SALE)
        } else {
            recommendedProductRepository.findAllByStatusAndCategoryOrderByDisplayOrderAscIdAsc(
                RecommendedProductStatus.ON_SALE,
                category,
            )
        }

        return products.map(RecommendedProductResponse::from)
    }

    @Transactional(readOnly = true)
    fun getRecommendedProduct(productId: Long): RecommendedProductResponse =
        RecommendedProductResponse.from(findProduct(productId))

    @Transactional(readOnly = true)
    fun getOnSaleProduct(productId: Long): RecommendedProduct {
        val product = findProduct(productId)
        if (product.status == RecommendedProductStatus.DISCONTINUED) {
            throw BusinessException(RecommendedProductErrorCode.RECOMMENDED_PRODUCT_DISCONTINUED)
        }

        return product
    }

    private fun findProduct(productId: Long): RecommendedProduct =
        recommendedProductRepository.findByIdOrNull(productId)
            ?: throw BusinessException(RecommendedProductErrorCode.RECOMMENDED_PRODUCT_NOT_FOUND)
}
