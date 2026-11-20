package com.botem.domain.funding.service

import com.botem.domain.account.entity.Bank
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
import com.botem.domain.funding.entity.FundingProductChangeHistory
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.FundingProductChangeHistoryRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.user.entity.User
import com.botem.domain.user.entity.UserStatus
import com.botem.domain.user.exception.UserErrorCode
import com.botem.domain.user.repository.UserRepository
import com.botem.domain.wish.entity.Wish
import com.botem.global.exception.BusinessException
import com.botem.global.exception.AuthErrorCode
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FundingProductChangeServiceTest {

    private val fundingRepository = mock(FundingRepository::class.java)
    private val historyRepository = mock(FundingProductChangeHistoryRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val resourceResolver = mock(FundingCreationResourceResolver::class.java)
    private val service = FundingProductChangeService(
        fundingRepository = fundingRepository,
        productChangeHistoryRepository = historyRepository,
        userRepository = userRepository,
        resourceResolver = resourceResolver,
        clock = Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), ZoneId.of("Asia/Seoul")),
    )

    @Test
    fun `개설자의 다른 위시로 상품 스냅샷을 변경하고 이력을 저장한다`() {
        givenActiveUser()
        val funding = funding()
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)
        given(resourceResolver.resolveWish(OWNER_ID, NEW_WISH_ID)).willReturn(wish())
        given(historyRepository.save(any(FundingProductChangeHistory::class.java))).willAnswer { it.arguments[0] }

        val response = service.change(OWNER_ID, FUNDING_ID, NEW_WISH_ID)

        assertEquals("새 헤드폰", response.product.name)
        assertEquals("BOTEM 브랜드샵", response.product.sellerName)
        assertEquals(NOW, response.productChangedAt)
        assertEquals(100_000L, funding.targetAmount)
        val captor = ArgumentCaptor.forClass(FundingProductChangeHistory::class.java)
        verify(historyRepository).save(captor.capture())
        assertEquals("무선 헤드폰", captor.value.previousProductName)
        assertEquals("새 헤드폰", captor.value.changedProductName)
    }

    @Test
    fun `현재 상품과 같은 위시 재요청은 위시 조회와 이력 저장을 반복하지 않는다`() {
        givenActiveUser()
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding())

        val response = service.change(OWNER_ID, FUNDING_ID, CURRENT_WISH_ID)

        assertEquals("무선 헤드폰", response.product.name)
        verify(resourceResolver, never()).resolveWish(OWNER_ID, CURRENT_WISH_ID)
        verify(historyRepository, never()).save(any(FundingProductChangeHistory::class.java))
    }

    @Test
    fun `개설자가 아니면 위시를 조회하지 않고 상품 변경을 거부한다`() {
        given(userRepository.findByIdForUpdate(OTHER_USER_ID)).willReturn(activeUser())
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding())

        val exception = assertFailsWith<BusinessException> {
            service.change(OTHER_USER_ID, FUNDING_ID, NEW_WISH_ID)
        }

        assertEquals(FundingErrorCode.FUNDING_OWNER_ONLY, exception.errorCode)
        verify(resourceResolver, never()).resolveWish(OTHER_USER_ID, NEW_WISH_ID)
    }

    @Test
    fun `펀딩이 없으면 상품 변경을 거부한다`() {
        givenActiveUser()
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(null)

        val exception = assertFailsWith<BusinessException> {
            service.change(OWNER_ID, FUNDING_ID, NEW_WISH_ID)
        }

        assertEquals(FundingErrorCode.FUNDING_NOT_FOUND, exception.errorCode)
        verify(resourceResolver, never()).resolveWish(OWNER_ID, NEW_WISH_ID)
    }

    @Test
    fun `취소된 펀딩은 찾을 수 없는 것으로 처리한다`() {
        givenActiveUser()
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(
            funding(status = FundingStatus.CANCELLED),
        )

        val exception = assertFailsWith<BusinessException> {
            service.change(OWNER_ID, FUNDING_ID, NEW_WISH_ID)
        }

        assertEquals(FundingErrorCode.FUNDING_NOT_FOUND, exception.errorCode)
        verify(resourceResolver, never()).resolveWish(OWNER_ID, NEW_WISH_ID)
    }

    @Test
    fun `원본 위시 연결이 없는 기존 상품도 새 위시 상품으로 변경한다`() {
        givenActiveUser()
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding(sourceWishId = null))
        given(resourceResolver.resolveWish(OWNER_ID, NEW_WISH_ID)).willReturn(wish())
        given(historyRepository.save(any(FundingProductChangeHistory::class.java))).willAnswer { it.arguments[0] }

        val response = service.change(OWNER_ID, FUNDING_ID, NEW_WISH_ID)

        assertEquals("새 헤드폰", response.product.name)
        verify(historyRepository).save(any(FundingProductChangeHistory::class.java))
    }

    @Test
    fun `마감된 펀딩은 상품을 변경할 수 없다`() {
        givenActiveUser()
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding(deadline = NOW))
        given(resourceResolver.resolveWish(OWNER_ID, NEW_WISH_ID)).willReturn(wish())

        val exception = assertFailsWith<BusinessException> {
            service.change(OWNER_ID, FUNDING_ID, NEW_WISH_ID)
        }

        assertEquals(FundingErrorCode.FUNDING_PRODUCT_CHANGE_NOT_AVAILABLE, exception.errorCode)
        verify(historyRepository, never()).save(any(FundingProductChangeHistory::class.java))
    }

    @Test
    fun `존재하지 않거나 탈퇴한 사용자는 상품을 변경할 수 없다`() {
        given(userRepository.findByIdForUpdate(OWNER_ID)).willReturn(null)
        assertEquals(
            UserErrorCode.USER_NOT_FOUND,
            assertFailsWith<BusinessException> {
                service.change(OWNER_ID, FUNDING_ID, NEW_WISH_ID)
            }.errorCode,
        )

        given(userRepository.findByIdForUpdate(OWNER_ID)).willReturn(activeUser(UserStatus.WITHDRAWN))
        assertEquals(
            AuthErrorCode.WITHDRAWN_USER,
            assertFailsWith<BusinessException> {
                service.change(OWNER_ID, FUNDING_ID, NEW_WISH_ID)
            }.errorCode,
        )
        verify(fundingRepository, never()).findByIdForUpdate(FUNDING_ID)
    }

    private fun givenActiveUser() {
        given(userRepository.findByIdForUpdate(OWNER_ID)).willReturn(activeUser())
    }

    private fun activeUser(status: UserStatus = UserStatus.ACTIVE): User =
        User(kakaoId = 1234L, nickname = "지유", profileImageUrl = null, status = status)

    private fun wish(): Wish =
        Wish(
            userId = OWNER_ID,
            productName = "새 헤드폰",
            productImageUrl = "https://image.example.com/new.jpg",
            price = 120_000L,
            productUrl = "https://shop.example.com/new",
            sellerName = "BOTEM 브랜드샵",
        ).also { setField(it, "id", NEW_WISH_ID) }

    private fun funding(
        deadline: LocalDateTime = NOW.plusDays(3),
        status: FundingStatus = FundingStatus.FUNDING,
        sourceWishId: Long? = CURRENT_WISH_ID,
    ): Funding =
        Funding(
            ownerId = OWNER_ID,
            recipientId = null,
            type = FundingType.BIRTHDAY,
            title = "생일 선물 펀딩",
            targetAmount = 100_000L,
            deadline = deadline,
            revealAt = null,
            story = "새 헤드폰이 필요해요",
            promise = null,
            product = FundingProductSnapshot(sourceWishId, "무선 헤드폰", "https://image.example.com/product.jpg", 100_000L, "https://shop.example.com/product"),
            payoutAccount = FundingPayoutAccountSnapshot(1L, Bank.KAKAO_BANK, "encrypted", "지유"),
        ).also {
            setField(it, "id", FUNDING_ID)
            setField(it, "status", status)
        }

    private fun setField(target: Any, fieldName: String, value: Any?) {
        target.javaClass.getDeclaredField(fieldName).apply {
            isAccessible = true
            set(target, value)
        }
    }

    companion object {
        private const val OWNER_ID = 1L
        private const val OTHER_USER_ID = 2L
        private const val FUNDING_ID = 12L
        private const val CURRENT_WISH_ID = 1L
        private const val NEW_WISH_ID = 2L
        private val NOW = LocalDateTime.of(2026, 11, 18, 12, 0)
    }
}
