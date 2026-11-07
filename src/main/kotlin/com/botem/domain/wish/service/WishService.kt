package com.botem.domain.wish.service

import com.botem.domain.product.service.RecommendedProductService
import com.botem.domain.wish.dto.response.WishResponse
import com.botem.domain.wish.entity.Wish
import com.botem.domain.wish.exception.WishErrorCode
import com.botem.domain.wish.repository.WishRepository
import com.botem.global.exception.BusinessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WishService(
    private val wishRepository: WishRepository,
    private val recommendedProductService: RecommendedProductService,
) {

    // 운영자가 나중에 상품 정보를 바꿔도 담은 시점의 위시는 그대로 두도록 상품 정보를 복사해 저장
    @Transactional
    fun addRecommendedProduct(userId: Long, recommendedProductId: Long): WishResponse {
        val product = recommendedProductService.getOnSaleProduct(recommendedProductId)
        if (wishRepository.existsByUserIdAndRecommendedProductId(userId, recommendedProductId)) {
            throw BusinessException(WishErrorCode.WISH_ALREADY_ADDED)
        }

        val wish = Wish(
            userId = userId,
            productName = product.name,
            productImageUrl = product.imageUrl,
            price = product.price,
            productUrl = product.productUrl,
            recommendedProductId = product.id,
        )

        // 동시에 두 번 눌러 중복 확인을 함께 통과해도 유니크 제약으로 한 건만 저장
        val savedWish = try {
            wishRepository.saveAndFlush(wish)
        } catch (exception: DataIntegrityViolationException) {
            throw BusinessException(WishErrorCode.WISH_ALREADY_ADDED)
        }

        return WishResponse.from(savedWish)
    }
}
