package com.botem.domain.product.service

import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.product.dto.request.CreateRecommendedProductRequest
import com.botem.domain.product.dto.response.RecommendedProductResponse
import com.botem.domain.product.entity.ProductCategory
import com.botem.domain.product.entity.RecommendedProduct
import com.botem.domain.product.entity.RecommendedProductStatus
import com.botem.domain.product.exception.ProductErrorCode
import com.botem.domain.product.repository.RecommendedProductRepository
import com.botem.domain.wish.repository.WishRepository
import com.botem.global.exception.BusinessException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RecommendedProductService(
    private val recommendedProductRepository: RecommendedProductRepository,
    private val wishRepository: WishRepository,
    private val fundingRepository: FundingRepository,
) {

    // 판매 종료 상품은 목록에서 숨기고, 공유 링크나 담긴 위시로 들어온 상세 조회에서만 상태와 함께 노출
    @Transactional(readOnly = true)
    fun getRecommendedProducts(userId: Long, category: ProductCategory?): List<RecommendedProductResponse> {
        val products = if (category == null) {
            recommendedProductRepository.findAllByStatusOrderByDisplayOrderAscIdAsc(RecommendedProductStatus.ON_SALE)
        } else {
            recommendedProductRepository.findAllByStatusAndCategoryOrderByDisplayOrderAscIdAsc(
                RecommendedProductStatus.ON_SALE,
                category,
            )
        }

        return toResponses(userId, products)
    }

    @Transactional
    fun createRecommendedProduct(request: CreateRecommendedProductRequest): RecommendedProductResponse {
        val product = RecommendedProduct(
            name = request.name,
            imageUrl = request.imageUrl,
            price = request.price,
            sellerName = request.sellerName,
            productUrl = request.productUrl,
            category = request.category,
            displayOrder = request.displayOrder,
        )

        return RecommendedProductResponse.from(recommendedProductRepository.save(product))
    }

    @Transactional(readOnly = true)
    fun getRecommendedProduct(userId: Long, productId: Long): RecommendedProductResponse =
        toResponses(userId, listOf(findProduct(productId))).single()

    @Transactional(readOnly = true)
    fun getOnSaleProduct(productId: Long): RecommendedProduct {
        val product = findProduct(productId)
        if (product.status == RecommendedProductStatus.DISCONTINUED) {
            throw BusinessException(ProductErrorCode.RECOMMENDED_PRODUCT_DISCONTINUED)
        }

        return product
    }

    @Transactional(readOnly = true)
    fun findDiscontinuedProductIds(productIds: Collection<Long>): Set<Long> =
        recommendedProductRepository.findAllById(productIds)
            .filter { it.status == RecommendedProductStatus.DISCONTINUED }
            .mapTo(mutableSetOf()) { it.id }

    // 친구가 내 위시로 연 서프라이즈가 드러나지 않도록 위시 API와 같이 내가 연 펀딩만 반영
    private fun toResponses(userId: Long, products: List<RecommendedProduct>): List<RecommendedProductResponse> {
        if (products.isEmpty()) return emptyList()

        val wishIdByProductId = wishRepository
            .findAllByUserIdAndRecommendedProductIdIn(userId, products.map { it.id })
            .associate { it.recommendedProductId to it.id }
        val fundingIdByWishId = if (wishIdByProductId.isEmpty()) {
            emptyMap()
        } else {
            fundingRepository
                .findAllByOwnerIdAndProductSourceWishIdInAndStatusNotIn(
                    userId,
                    wishIdByProductId.values,
                    ENDED_FUNDING_STATUSES,
                )
                .associate { it.product.sourceWishId to it.id }
        }

        return products.map { product ->
            val wishId = wishIdByProductId[product.id]
            RecommendedProductResponse.from(product, wishId, wishId?.let(fundingIdByWishId::get))
        }
    }

    private fun findProduct(productId: Long): RecommendedProduct =
        recommendedProductRepository.findByIdOrNull(productId)
            ?: throw BusinessException(ProductErrorCode.RECOMMENDED_PRODUCT_NOT_FOUND)

    companion object {
        private val ENDED_FUNDING_STATUSES = setOf(FundingStatus.COMPLETED, FundingStatus.CANCELLED)
    }
}
