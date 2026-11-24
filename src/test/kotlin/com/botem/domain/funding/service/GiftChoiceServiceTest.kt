package com.botem.domain.funding.service

import com.botem.domain.account.entity.Bank
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.entity.GiftVerification
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.GiftVerificationRepository
import com.botem.global.exception.BusinessException
import org.junit.jupiter.api.Test
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
import kotlin.test.assertNull

class GiftChoiceServiceTest {

    private val fundingRepository = mock(FundingRepository::class.java)
    private val giftVerificationRepository = mock(GiftVerificationRepository::class.java)
    private val service = GiftChoiceService(
        fundingRepository,
        giftVerificationRepository,
        Clock.fixed(Instant.parse("2026-11-20T03:00:00Z"), ZoneId.of("Asia/Seoul")),
    )

    @Test
    fun `결과를 연 생일 펀딩의 선물을 정하면 인증 대기를 만들고 구매 링크를 반환한다`() {
        val funding = openedFunding()
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)
        given(giftVerificationRepository.findByFundingId(FUNDING_ID)).willReturn(null)
        given(giftVerificationRepository.save(any(GiftVerification::class.java))).willAnswer { invocation ->
            (invocation.arguments[0] as GiftVerification).also { setField(it, GiftVerification::class.java, "id", VERIFICATION_ID) }
        }

        val response = service.choose(OWNER_ID, FUNDING_ID)

        assertEquals(FundingStatus.WAITING_GIFT_VERIFICATION, funding.status)
        assertEquals(VERIFICATION_ID, response.giftVerificationId)
        assertEquals("https://shop.example.com/product", response.productUrl)
    }

    @Test
    fun `이미 선물을 정했으면 기존 인증 대기를 그대로 반환한다`() {
        val funding = openedFunding().also { it.chooseGift() }
        val existing = GiftVerification(FUNDING_ID).also { setField(it, GiftVerification::class.java, "id", VERIFICATION_ID) }
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)
        given(giftVerificationRepository.findByFundingId(FUNDING_ID)).willReturn(existing)

        val response = service.choose(OWNER_ID, FUNDING_ID)

        assertEquals(VERIFICATION_ID, response.giftVerificationId)
        verify(giftVerificationRepository, never()).save(any())
    }

    @Test
    fun `수신자가 공개된 서프라이즈의 선물을 정하면 완료하고 재요청에도 같은 결과를 반환한다`() {
        val funding = revealedSurpriseFunding()
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)

        val first = service.choose(RECIPIENT_ID, FUNDING_ID)
        val retried = service.choose(RECIPIENT_ID, FUNDING_ID)

        assertEquals(FundingStatus.COMPLETED, funding.status)
        assertNull(first.giftVerificationId)
        assertNull(retried.giftVerificationId)
        assertEquals("https://shop.example.com/product", first.productUrl)
        verify(giftVerificationRepository, never()).save(any(GiftVerification::class.java))
    }

    @Test
    fun `서프라이즈 수령자가 아니면 선물을 정할 수 없다`() {
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(revealedSurpriseFunding())

        val exception = assertFailsWith<BusinessException> { service.choose(OWNER_ID, FUNDING_ID) }

        assertEquals(FundingErrorCode.SURPRISE_RECIPIENT_ONLY, exception.errorCode)
        verify(giftVerificationRepository, never()).save(any())
    }

    @Test
    fun `공개 전 서프라이즈는 수령자도 선물을 정할 수 없다`() {
        val funding = revealedSurpriseFunding().also {
            setField(it, Funding::class.java, "status", FundingStatus.WAITING_REVEAL)
        }
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)

        val exception = assertFailsWith<BusinessException> { service.choose(RECIPIENT_ID, FUNDING_ID) }

        assertEquals(FundingErrorCode.GIFT_CHOICE_NOT_AVAILABLE, exception.errorCode)
        verify(giftVerificationRepository, never()).save(any())
    }

    @Test
    fun `펀딩이 없거나 취소됐거나 개설자가 아니면 선물을 정할 수 없다`() {
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(null)
        assertEquals(
            FundingErrorCode.FUNDING_NOT_FOUND,
            assertFailsWith<BusinessException> { service.choose(OWNER_ID, FUNDING_ID) }.errorCode,
        )

        given(fundingRepository.findByIdForUpdate(FUNDING_ID))
            .willReturn(openedFunding().also { setField(it, Funding::class.java, "status", FundingStatus.CANCELLED) })
        assertEquals(
            FundingErrorCode.FUNDING_NOT_FOUND,
            assertFailsWith<BusinessException> { service.choose(OWNER_ID, FUNDING_ID) }.errorCode,
        )

        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(openedFunding())
        assertEquals(
            FundingErrorCode.FUNDING_OWNER_ONLY,
            assertFailsWith<BusinessException> { service.choose(OTHER_USER_ID, FUNDING_ID) }.errorCode,
        )
        verify(giftVerificationRepository, never()).save(any())
    }

    private fun openedFunding(): Funding =
        Funding(
            ownerId = OWNER_ID,
            recipientId = null,
            type = FundingType.BIRTHDAY,
            title = "생일 선물 펀딩",
            targetAmount = 100_000L,
            deadline = NOW.minusDays(1),
            revealAt = null,
            story = null,
            promise = null,
            product = FundingProductSnapshot(
                null,
                "무선 헤드폰",
                "https://image.example.com/product.jpg",
                100_000L,
                "https://shop.example.com/product",
            ),
            payoutAccount = FundingPayoutAccountSnapshot(null, Bank.KAKAO_BANK, "encrypted", "지유"),
        ).also {
            setField(it, Funding::class.java, "id", FUNDING_ID)
            it.openResult(NOW)
        }

    private fun revealedSurpriseFunding(): Funding =
        Funding(
            ownerId = OWNER_ID,
            recipientId = RECIPIENT_ID,
            type = FundingType.SURPRISE,
            title = null,
            targetAmount = 100_000L,
            deadline = NOW.minusDays(1),
            revealAt = NOW.minusMinutes(1),
            story = null,
            promise = null,
            product = FundingProductSnapshot(
                null,
                "무선 헤드폰",
                "https://image.example.com/product.jpg",
                100_000L,
                "https://shop.example.com/product",
            ),
            payoutAccount = FundingPayoutAccountSnapshot(null, Bank.KAKAO_BANK, "encrypted", "지유"),
        ).also {
            setField(it, Funding::class.java, "id", FUNDING_ID)
            setField(it, Funding::class.java, "status", FundingStatus.REVEALED)
        }

    private fun setField(target: Any, type: Class<*>, fieldName: String, value: Any) {
        type.getDeclaredField(fieldName).apply {
            isAccessible = true
            set(target, value)
        }
    }

    companion object {
        private const val OWNER_ID = 1L
        private const val OTHER_USER_ID = 2L
        private const val RECIPIENT_ID = 3L
        private const val FUNDING_ID = 10L
        private const val VERIFICATION_ID = 7L
        private val NOW = LocalDateTime.of(2026, 11, 20, 12, 0)
    }
}
