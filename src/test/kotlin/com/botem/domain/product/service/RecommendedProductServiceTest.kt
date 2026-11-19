package com.botem.domain.product.service

import com.botem.domain.account.entity.Bank
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.product.entity.ProductCategory
import com.botem.domain.product.entity.RecommendedProduct
import com.botem.domain.product.entity.RecommendedProductStatus
import com.botem.domain.product.exception.ProductErrorCode
import com.botem.domain.product.repository.RecommendedProductRepository
import com.botem.domain.wish.entity.Wish
import com.botem.domain.wish.repository.WishRepository
import com.botem.global.config.JpaAuditingConfig
import com.botem.global.exception.BusinessException
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@DataJpaTest
@Import(JpaAuditingConfig::class, RecommendedProductService::class)
class RecommendedProductServiceTest {

    @Autowired
    private lateinit var recommendedProductRepository: RecommendedProductRepository

    @Autowired
    private lateinit var recommendedProductService: RecommendedProductService

    @Autowired
    private lateinit var wishRepository: WishRepository

    @Autowired
    private lateinit var fundingRepository: FundingRepository

    @Test
    fun `목록은 판매 중인 상품만 노출 순서대로 조회하고 카테고리로 거를 수 있다`() {
        save("다이슨 에어랩", ProductCategory.HAIR_BEAUTY_DEVICE, displayOrder = 2)
        save("에어팟 프로 3", ProductCategory.EARPHONE_HEADPHONE, displayOrder = 1)
        save("소니 WH-1000XM6", ProductCategory.EARPHONE_HEADPHONE, displayOrder = 3)
        save("판매 종료 헤드폰", ProductCategory.EARPHONE_HEADPHONE, RecommendedProductStatus.DISCONTINUED)

        assertEquals(
            listOf("에어팟 프로 3", "다이슨 에어랩", "소니 WH-1000XM6"),
            recommendedProductService.getRecommendedProducts(USER_ID, null).map { it.name },
        )
        assertEquals(
            listOf("에어팟 프로 3", "소니 WH-1000XM6"),
            recommendedProductService.getRecommendedProducts(USER_ID, ProductCategory.EARPHONE_HEADPHONE).map { it.name },
        )
    }

    @Test
    fun `판매 종료 상품도 상세 조회할 수 있고 상태를 함께 반환한다`() {
        val product = save("판매 종료 헤드폰", ProductCategory.EARPHONE_HEADPHONE, RecommendedProductStatus.DISCONTINUED)

        assertEquals(
            RecommendedProductStatus.DISCONTINUED,
            recommendedProductService.getRecommendedProduct(USER_ID, product.id).status,
        )
    }

    @Test
    fun `없는 상품을 상세 조회하면 예외가 발생한다`() {
        val exception = assertFailsWith<BusinessException> {
            recommendedProductService.getRecommendedProduct(USER_ID, 999L)
        }

        assertEquals(ProductErrorCode.RECOMMENDED_PRODUCT_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `목록은 내 위시에 담은 상품의 위시 ID와 내가 연 진행 중 펀딩 ID를 함께 반환한다`() {
        val notInWish = save("에어팟 프로 3", ProductCategory.EARPHONE_HEADPHONE, displayOrder = 1)
        val inWish = save("다이슨 에어랩", ProductCategory.HAIR_BEAUTY_DEVICE, displayOrder = 2)
        val inFunding = save("소니 WH-1000XM6", ProductCategory.EARPHONE_HEADPHONE, displayOrder = 3)
        val wishOnly = saveWish(USER_ID, inWish)
        val fundingWish = saveWish(USER_ID, inFunding)
        val funding = saveFunding(ownerId = USER_ID, sourceWishId = fundingWish.id)
        saveWish(OTHER_USER_ID, notInWish)

        val responses = recommendedProductService.getRecommendedProducts(USER_ID, null).associateBy { it.id }

        assertNull(responses.getValue(notInWish.id).wishId)
        assertNull(responses.getValue(notInWish.id).fundingId)
        assertEquals(wishOnly.id, responses.getValue(inWish.id).wishId)
        assertNull(responses.getValue(inWish.id).fundingId)
        assertEquals(fundingWish.id, responses.getValue(inFunding.id).wishId)
        assertEquals(funding.id, responses.getValue(inFunding.id).fundingId)
    }

    @Test
    fun `친구가 내 위시로 연 서프라이즈와 끝난 펀딩은 펀딩 ID에 반영하지 않는다`() {
        val surpriseProduct = save("다이슨 에어랩", ProductCategory.HAIR_BEAUTY_DEVICE)
        val endedProduct = save("소니 WH-1000XM6", ProductCategory.EARPHONE_HEADPHONE)
        val surpriseWish = saveWish(USER_ID, surpriseProduct)
        val endedWish = saveWish(USER_ID, endedProduct)
        saveFunding(ownerId = OTHER_USER_ID, sourceWishId = surpriseWish.id, recipientId = USER_ID)
        saveFunding(ownerId = USER_ID, sourceWishId = endedWish.id, status = FundingStatus.COMPLETED)

        val surprise = recommendedProductService.getRecommendedProduct(USER_ID, surpriseProduct.id)
        val ended = recommendedProductService.getRecommendedProduct(USER_ID, endedProduct.id)

        assertEquals(surpriseWish.id, surprise.wishId)
        assertNull(surprise.fundingId)
        assertEquals(endedWish.id, ended.wishId)
        assertNull(ended.fundingId)
    }

    @Test
    fun `판매 종료 상품으로 펀딩 중이면 판매 상태와 펀딩 ID를 모두 반환한다`() {
        val product = save("판매 종료 헤드폰", ProductCategory.EARPHONE_HEADPHONE, RecommendedProductStatus.DISCONTINUED)
        val wish = saveWish(USER_ID, product)
        val funding = saveFunding(ownerId = USER_ID, sourceWishId = wish.id)

        val response = recommendedProductService.getRecommendedProduct(USER_ID, product.id)

        assertEquals(RecommendedProductStatus.DISCONTINUED, response.status)
        assertEquals(wish.id, response.wishId)
        assertEquals(funding.id, response.fundingId)
    }

    private fun saveWish(userId: Long, product: RecommendedProduct): Wish =
        wishRepository.save(
            Wish(
                userId = userId,
                productName = product.name,
                productImageUrl = product.imageUrl,
                price = product.price,
                productUrl = product.productUrl,
                sellerName = product.sellerName,
                recommendedProductId = product.id,
            ),
        )

    private fun saveFunding(
        ownerId: Long,
        sourceWishId: Long,
        recipientId: Long? = null,
        status: FundingStatus = FundingStatus.FUNDING,
    ): Funding {
        val deadline = LocalDateTime.of(2026, 12, 12, 23, 59, 59)
        val funding = Funding(
            ownerId = ownerId,
            recipientId = recipientId,
            type = if (recipientId == null) FundingType.BIRTHDAY else FundingType.SURPRISE,
            title = if (recipientId == null) "생일 펀딩" else null,
            targetAmount = 350_000,
            deadline = deadline,
            revealAt = recipientId?.let { deadline.plusSeconds(1) },
            story = null,
            promise = null,
            product = FundingProductSnapshot(sourceWishId, "상품", "https://example.com/image.jpg", 350_000, "https://example.com"),
            payoutAccount = FundingPayoutAccountSnapshot(null, Bank.KAKAO_BANK, "encrypted", "주최자"),
        )
        Funding::class.java.getDeclaredField("status").apply {
            isAccessible = true
            set(funding, status)
        }

        return fundingRepository.save(funding)
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

    companion object {
        private const val USER_ID = 1L
        private const val OTHER_USER_ID = 2L
    }
}
