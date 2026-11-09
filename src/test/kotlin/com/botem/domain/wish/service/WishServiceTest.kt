package com.botem.domain.wish.service

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
import com.botem.domain.product.service.RecommendedProductService
import com.botem.domain.wish.entity.Wish
import com.botem.domain.wish.entity.WishStatus
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
import java.time.LocalDateTime
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

@DataJpaTest
@Import(JpaAuditingConfig::class, WishService::class, RecommendedProductService::class)
class WishServiceTest {

    @Autowired
    private lateinit var wishService: WishService

    @Autowired
    private lateinit var wishRepository: WishRepository

    @Autowired
    private lateinit var recommendedProductRepository: RecommendedProductRepository

    @Autowired
    private lateinit var fundingRepository: FundingRepository

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

        assertEquals(ProductErrorCode.RECOMMENDED_PRODUCT_DISCONTINUED, exception.errorCode)
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
    fun `같은 회원은 같은 추천 상품을 DB에 두 번 저장할 수 없다`() {
        val product = saveProduct()
        wishRepository.saveAndFlush(wishOf(product))

        assertFailsWith<DataIntegrityViolationException> {
            wishRepository.saveAndFlush(wishOf(product))
        }
    }

    @Test
    fun `위시 목록은 최신순으로 펀딩 중과 판매 종료 상태를 함께 반환한다`() {
        val discontinued = saveProduct(name = "판매 종료", status = RecommendedProductStatus.DISCONTINUED)
        val discontinuedWish = wishRepository.save(wishOf(discontinued))
        val fundingWish = wishRepository.save(wishOf(saveProduct(name = "펀딩 중")))
        val availableWish = wishRepository.save(wishOf(saveProduct(name = "담기만 함")))
        val funding = saveFunding(sourceWishId = fundingWish.id, status = FundingStatus.WAITING_GIFT_VERIFICATION)

        val response = wishService.getWishes(USER_ID, page = 0, size = 10)

        assertEquals(listOf(availableWish.id, fundingWish.id, discontinuedWish.id), response.content.map { it.id })
        assertEquals(
            listOf(WishStatus.AVAILABLE, WishStatus.FUNDING, WishStatus.DISCONTINUED),
            response.content.map { it.status },
        )
        assertEquals(funding.id, response.content[1].fundingId)
    }

    @Test
    fun `끝난 펀딩이나 친구가 연 서프라이즈 펀딩은 위시 상태에 반영하지 않는다`() {
        val wish = wishRepository.save(wishOf(saveProduct()))
        saveFunding(sourceWishId = wish.id, status = FundingStatus.COMPLETED)
        saveFunding(sourceWishId = wish.id, ownerId = OTHER_USER_ID, type = FundingType.SURPRISE)

        val response = wishService.getWish(USER_ID, wish.id)

        assertEquals(WishStatus.AVAILABLE, response.status)
        assertNull(response.fundingId)
    }

    @Test
    fun `다른 회원의 위시는 조회하거나 삭제할 수 없다`() {
        val wish = wishRepository.save(wishOf(saveProduct()))

        listOf(
            { wishService.getWish(OTHER_USER_ID, wish.id) },
            { wishService.deleteWish(OTHER_USER_ID, wish.id) },
        ).forEach { action ->
            val exception = assertFailsWith<BusinessException> { action() }
            assertEquals(WishErrorCode.WISH_NOT_FOUND, exception.errorCode)
        }
    }

    @Test
    fun `끝나지 않은 펀딩에 연결된 위시는 삭제할 수 없고 끝나면 삭제할 수 있다`() {
        val activeWish = wishRepository.save(wishOf(saveProduct(name = "진행 중")))
        val endedWish = wishRepository.save(wishOf(saveProduct(name = "끝남")))
        saveFunding(sourceWishId = activeWish.id, status = FundingStatus.CLOSED)
        saveFunding(sourceWishId = endedWish.id, status = FundingStatus.CANCELLED)

        val exception = assertFailsWith<BusinessException> {
            wishService.deleteWish(USER_ID, activeWish.id)
        }
        wishService.deleteWish(USER_ID, endedWish.id)

        assertEquals(WishErrorCode.WISH_IN_FUNDING, exception.errorCode)
        assertFalse(wishRepository.existsById(endedWish.id))
    }

    @Test
    fun `페이지 크기가 범위를 벗어나면 조회할 수 없다`() {
        listOf(0 to 0, 0 to 21, -1 to 10).forEach { (page, size) ->
            assertFailsWith<BusinessException> { wishService.getWishes(USER_ID, page, size) }
        }
    }

    private fun saveFunding(
        sourceWishId: Long,
        status: FundingStatus = FundingStatus.FUNDING,
        ownerId: Long = USER_ID,
        type: FundingType = FundingType.BIRTHDAY,
    ): Funding {
        val funding = Funding(
            ownerId = ownerId,
            recipientId = if (type == FundingType.SURPRISE) USER_ID else null,
            type = type,
            title = "생일 선물 펀딩",
            targetAmount = 100_000L,
            deadline = LocalDateTime.of(2026, 12, 1, 0, 0),
            revealAt = null,
            story = null,
            promise = null,
            product = FundingProductSnapshot(
                sourceWishId = sourceWishId,
                name = "에어팟 프로 3",
                imageUrl = "https://example.com/image.jpg",
                price = 100_000L,
                url = "https://link.coupang.com/a/example",
            ),
            payoutAccount = FundingPayoutAccountSnapshot(
                sourcePayoutAccountId = null,
                bank = Bank.KAKAO_BANK,
                encryptedAccountNumber = "encrypted-account-number",
                accountHolder = "지유",
            ),
        )
        Funding::class.java.getDeclaredField("status").apply {
            isAccessible = true
            set(funding, status)
        }

        return fundingRepository.save(funding)
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
