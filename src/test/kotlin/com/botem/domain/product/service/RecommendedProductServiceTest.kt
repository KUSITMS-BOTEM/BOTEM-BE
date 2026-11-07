package com.botem.domain.product.service

import com.botem.domain.product.entity.ProductCategory
import com.botem.domain.product.entity.RecommendedProduct
import com.botem.domain.product.entity.RecommendedProductStatus
import com.botem.domain.product.exception.RecommendedProductErrorCode
import com.botem.domain.product.repository.RecommendedProductRepository
import com.botem.global.config.JpaAuditingConfig
import com.botem.global.exception.BusinessException
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DataJpaTest
@Import(JpaAuditingConfig::class, RecommendedProductService::class)
class RecommendedProductServiceTest {

    @Autowired
    private lateinit var recommendedProductRepository: RecommendedProductRepository

    @Autowired
    private lateinit var recommendedProductService: RecommendedProductService

    @Test
    fun `목록은 판매 중인 상품만 노출 순서대로 조회하고 카테고리로 거를 수 있다`() {
        save("다이슨 에어랩", ProductCategory.HAIR_BEAUTY_DEVICE, displayOrder = 2)
        save("에어팟 프로 3", ProductCategory.EARPHONE_HEADPHONE, displayOrder = 1)
        save("소니 WH-1000XM6", ProductCategory.EARPHONE_HEADPHONE, displayOrder = 3)
        save("판매 종료 헤드폰", ProductCategory.EARPHONE_HEADPHONE, RecommendedProductStatus.DISCONTINUED)

        assertEquals(
            listOf("에어팟 프로 3", "다이슨 에어랩", "소니 WH-1000XM6"),
            recommendedProductService.getRecommendedProducts(null).map { it.name },
        )
        assertEquals(
            listOf("에어팟 프로 3", "소니 WH-1000XM6"),
            recommendedProductService.getRecommendedProducts(ProductCategory.EARPHONE_HEADPHONE).map { it.name },
        )
    }

    @Test
    fun `판매 종료 상품도 상세 조회할 수 있고 상태를 함께 반환한다`() {
        val product = save("판매 종료 헤드폰", ProductCategory.EARPHONE_HEADPHONE, RecommendedProductStatus.DISCONTINUED)

        assertEquals(
            RecommendedProductStatus.DISCONTINUED,
            recommendedProductService.getRecommendedProduct(product.id).status,
        )
    }

    @Test
    fun `없는 상품을 상세 조회하면 예외가 발생한다`() {
        val exception = assertFailsWith<BusinessException> {
            recommendedProductService.getRecommendedProduct(999L)
        }

        assertEquals(RecommendedProductErrorCode.RECOMMENDED_PRODUCT_NOT_FOUND, exception.errorCode)
    }

    private fun save(
        name: String,
        category: ProductCategory,
        status: RecommendedProductStatus = RecommendedProductStatus.ON_SALE,
        displayOrder: Int = 0,
    ): RecommendedProduct =
        recommendedProductRepository.save(
            RecommendedProduct(
                name = name,
                imageUrl = "https://example.com/image.jpg",
                price = 350_000,
                sellerName = "공식 브랜드관",
                productUrl = "https://link.coupang.com/a/example",
                category = category,
                status = status,
                displayOrder = displayOrder,
            ),
        )
}
