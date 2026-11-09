package com.botem.domain.wish.service

import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.product.entity.ProductCategory
import com.botem.domain.product.entity.RecommendedProduct
import com.botem.domain.product.service.RecommendedProductService
import com.botem.domain.wish.entity.Wish
import com.botem.domain.wish.exception.WishErrorCode
import com.botem.domain.wish.repository.WishRepository
import com.botem.global.exception.BusinessException
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.springframework.dao.DataIntegrityViolationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WishServiceConcurrencyTest {

    private val wishRepository = mock(WishRepository::class.java)
    private val recommendedProductService = mock(RecommendedProductService::class.java)
    private val wishService = WishService(wishRepository, mock(FundingRepository::class.java), recommendedProductService)

    @Test
    fun `동시 요청이 중복 확인을 함께 통과해 유니크 제약에 걸리면 이미 담은 상품 예외로 응답한다`() {
        given(recommendedProductService.getOnSaleProduct(PRODUCT_ID)).willReturn(
            RecommendedProduct(
                name = "에어팟 프로 3",
                imageUrl = "https://example.com/image.jpg",
                price = 350_000,
                sellerName = "Apple 공식 브랜드관",
                productUrl = "https://link.coupang.com/a/example",
                category = ProductCategory.EARPHONE_HEADPHONE,
            ),
        )
        given(wishRepository.existsByUserIdAndRecommendedProductId(USER_ID, PRODUCT_ID)).willReturn(false)
        given(wishRepository.saveAndFlush(any(Wish::class.java)))
            .willThrow(DataIntegrityViolationException("uk_wishes_user_recommended_product"))

        val exception = assertFailsWith<BusinessException> {
            wishService.addRecommendedProduct(USER_ID, PRODUCT_ID)
        }

        assertEquals(WishErrorCode.WISH_ALREADY_ADDED, exception.errorCode)
    }

    companion object {
        private const val USER_ID = 1L
        private const val PRODUCT_ID = 1L
    }
}
