package com.botem.domain.funding.entity

import com.botem.domain.account.entity.Bank
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.global.exception.BusinessException
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FundingTest {

    @Test
    fun `새 펀딩은 전달받은 정보와 초기 상태를 가진다`() {
        val deadline = OPENED_AT.plusDays(1)
        val funding = funding(deadline = deadline)

        assertEquals(1L, funding.ownerId)
        assertEquals(null, funding.recipientId)
        assertEquals(FundingType.BIRTHDAY, funding.type)
        assertEquals("생일 선물 펀딩", funding.title)
        assertEquals(100_000L, funding.targetAmount)
        assertEquals(deadline, funding.deadline)
        assertEquals(null, funding.revealAt)
        assertEquals(null, funding.story)
        assertEquals(null, funding.promise)
        assertEquals("무선 헤드폰", funding.product.name)
        assertEquals(Bank.KAKAO_BANK, funding.payoutAccount.bank)
        assertEquals(FundingStatus.FUNDING, funding.status)
        assertEquals(0, funding.extensionCount)
        assertEquals(null, funding.extendedAt)
        assertEquals(null, funding.resultOpenedAt)
        assertEquals(null, funding.giftPreparedAt)
        assertEquals(null, funding.completedAt)
    }

    @Test
    fun `마감된 생일 펀딩 결과를 열면 열람 시각을 기록하고 선물 준비 상태로 전환한다`() {
        val funding = funding(deadline = OPENED_AT.minusDays(1))

        funding.openResult(OPENED_AT)

        assertEquals(OPENED_AT, funding.resultOpenedAt)
        assertEquals(FundingStatus.GIFT_PREPARING, funding.status)
    }

    @Test
    fun `이미 연 펀딩 결과를 다시 열면 최초 열람 상태를 유지한다`() {
        val funding = funding(deadline = OPENED_AT.minusDays(1))
        funding.openResult(OPENED_AT)

        funding.openResult(OPENED_AT.plusHours(1))

        assertEquals(OPENED_AT, funding.resultOpenedAt)
        assertEquals(FundingStatus.GIFT_PREPARING, funding.status)
    }

    @Test
    fun `서프라이즈 펀딩 결과는 팡 열기로 처리할 수 없다`() {
        val funding = funding(
            type = FundingType.SURPRISE,
            deadline = OPENED_AT.minusDays(1),
            revealAt = OPENED_AT.minusHours(1),
        )

        val exception = assertFailsWith<BusinessException> { funding.openResult(OPENED_AT) }

        assertEquals(FundingErrorCode.BIRTHDAY_FUNDING_REQUIRED, exception.errorCode)
    }

    @Test
    fun `마감 전 펀딩 결과는 열 수 없다`() {
        val funding = funding(deadline = OPENED_AT.plusMinutes(1))

        val exception = assertFailsWith<BusinessException> { funding.openResult(OPENED_AT) }

        assertEquals(FundingErrorCode.FUNDING_NOT_ENDED, exception.errorCode)
    }

    @Test
    fun `결과 열람이 불가능한 상태의 펀딩은 열 수 없다`() {
        val funding = funding(deadline = OPENED_AT.minusDays(1))
        setField(funding, "status", FundingStatus.CANCELLED)

        val exception = assertFailsWith<BusinessException> { funding.openResult(OPENED_AT) }

        assertEquals(FundingErrorCode.FUNDING_RESULT_NOT_OPENABLE, exception.errorCode)
    }

    @Test
    fun `생일 펀딩 개설자와 서프라이즈 수신자를 선물 수신자로 판단한다`() {
        val birthday = funding(deadline = OPENED_AT)
        val surprise = funding(
            type = FundingType.SURPRISE,
            deadline = OPENED_AT,
            revealAt = OPENED_AT,
        )

        assertEquals(true, birthday.isGiftRecipient(1L))
        assertEquals(false, birthday.isGiftRecipient(2L))
        assertEquals(true, surprise.isGiftRecipient(2L))
        assertEquals(false, surprise.isGiftRecipient(1L))
    }

    @Test
    fun `선물 인증 대기 펀딩을 완료하면 상태와 완료 시각을 변경한다`() {
        val funding = funding(deadline = OPENED_AT.minusDays(1))
        setField(funding, "status", FundingStatus.WAITING_GIFT_VERIFICATION)

        funding.completeGiftVerification(OPENED_AT)

        assertEquals(FundingStatus.COMPLETED, funding.status)
        assertEquals(OPENED_AT, funding.completedAt)
    }

    @Test
    fun `완료된 펀딩의 선물 인증 완료 재요청은 최초 완료 상태를 유지한다`() {
        val funding = funding(deadline = OPENED_AT.minusDays(1))
        setField(funding, "status", FundingStatus.WAITING_GIFT_VERIFICATION)
        funding.completeGiftVerification(OPENED_AT)

        funding.completeGiftVerification(OPENED_AT.plusHours(1))

        assertEquals(OPENED_AT, funding.completedAt)
    }

    @Test
    fun `선물 인증 대기가 아닌 펀딩은 완료할 수 없다`() {
        val funding = funding(deadline = OPENED_AT.minusDays(1))

        val exception = assertFailsWith<BusinessException> {
            funding.completeGiftVerification(OPENED_AT)
        }

        assertEquals(FundingErrorCode.FUNDING_GIFT_VERIFICATION_NOT_COMPLETABLE, exception.errorCode)
    }

    private fun funding(
        type: FundingType = FundingType.BIRTHDAY,
        deadline: LocalDateTime,
        revealAt: LocalDateTime? = null,
    ): Funding =
        Funding(
            ownerId = 1L,
            recipientId = if (type == FundingType.SURPRISE) 2L else null,
            type = type,
            title = "생일 선물 펀딩",
            targetAmount = 100_000L,
            deadline = deadline,
            revealAt = revealAt,
            story = null,
            promise = null,
            product = FundingProductSnapshot(
                sourceWishId = 1L,
                name = "무선 헤드폰",
                imageUrl = "https://image.example.com/product.jpg",
                price = 100_000L,
                url = "https://shop.example.com/product",
            ),
            payoutAccount = FundingPayoutAccountSnapshot(
                sourcePayoutAccountId = 1L,
                bank = Bank.KAKAO_BANK,
                encryptedAccountNumber = "encrypted-account-number",
                accountHolder = "지유",
            ),
        )

    private fun setField(target: Any, fieldName: String, value: Any) {
        target.javaClass.getDeclaredField(fieldName).apply {
            isAccessible = true
            set(target, value)
        }
    }

    companion object {
        private val OPENED_AT = LocalDateTime.of(2026, 11, 15, 0, 0)
    }
}
