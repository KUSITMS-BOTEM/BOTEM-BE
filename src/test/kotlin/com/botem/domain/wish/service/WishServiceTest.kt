package com.botem.domain.wish.service

import com.botem.domain.product.entity.ProductCategory
import com.botem.domain.product.entity.RecommendedProduct
import com.botem.domain.product.entity.RecommendedProductStatus
import com.botem.domain.product.exception.RecommendedProductErrorCode
import com.botem.domain.product.repository.RecommendedProductRepository
import com.botem.domain.product.service.RecommendedProductService
import com.botem.domain.wish.entity.Wish
import com.botem.domain.wish.exception.WishErrorCode
import com.botem.domain.wish.repository.WishRepository
import com.botem.global.config.JpaAuditingConfig
import com.botem.global.exception.BusinessException
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DataJpaTest
@Import(JpaAuditingConfig::class, WishService::class, RecommendedProductService::class)
class WishServiceTest {

    @Autowired
    private lateinit var wishService: WishService

    @Autowired
    private lateinit var wishRepository: WishRepository

    @Autowired
    private lateinit var recommendedProductRepository: RecommendedProductRepository

    @Test
    fun `추천 상품 정보를 복사해 위시로 저장한다`() {
        val product = saveProduct(name = "가".repeat(100))

        val response = wishService.addRecommendedProduct(USER_ID, product.id)

        val wish = wishRepository.findById(response.id).orElseThrow()
        assertEquals(product.name, wish.productName)
        assertEquals(product.price, wish.price)
        assertEquals(product.productUrl, wish.productUrl)
        assertEquals(product.id, wish.recommendedProductId)
    }

    @Test
    fun `판매 종료된 상품은 담을 수 없다`() {
        val product = saveProduct(status = RecommendedProductStatus.DISCONTINUED)

        val exception = assertFailsWith<BusinessException> {
            wishService.addRecommendedProduct(USER_ID, product.id)
        }

        assertEquals(RecommendedProductErrorCode.RECOMMENDED_PRODUCT_DISCONTINUED, exception.errorCode)
    }

    @Test
    fun `이미 담은 상품은 다시 담을 수 없지만 다른 회원은 담을 수 있다`() {
        val product = saveProduct()
        wishService.addRecommendedProduct(USER_ID, product.id)

        val exception = assertFailsWith<BusinessException> {
            wishService.addRecommendedProduct(USER_ID, product.id)
        }

        assertEquals(WishErrorCode.WISH_ALREADY_ADDED, exception.errorCode)
        wishService.addRecommendedProduct(OTHER_USER_ID, product.id)
    }

    @Test
    fun `동시 요청으로 중복 확인을 통과해도 같은 추천 상품은 한 번만 저장된다`() {
        val product = saveProduct()
        wishRepository.saveAndFlush(wishOf(product))

        assertFailsWith<DataIntegrityViolationException> {
            wishRepository.saveAndFlush(wishOf(product))
        }
    }

    private fun wishOf(product: RecommendedProduct): Wish =
        Wish(
            userId = USER_ID,
            productName = product.name,
            productImageUrl = product.imageUrl,
            price = product.price,
            productUrl = product.productUrl,
            recommendedProductId = product.id,
        )

    private fun saveProduct(
        name: String = "에어팟 프로 3",
        status: RecommendedProductStatus = RecommendedProductStatus.ON_SALE,
    ): RecommendedProduct =
        recommendedProductRepository.save(
            RecommendedProduct(
                name = name,
                imageUrl = "https://example.com/image.jpg",
                price = 350_000,
                sellerName = "Apple 공식 브랜드관",
                productUrl = "https://link.coupang.com/a/example",
                category = ProductCategory.EARPHONE_HEADPHONE,
                status = status,
            ),
        )

    companion object {
        private const val USER_ID = 1L
        private const val OTHER_USER_ID = 2L
    }
}
