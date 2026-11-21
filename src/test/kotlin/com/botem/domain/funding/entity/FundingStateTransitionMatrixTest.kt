package com.botem.domain.funding.entity

import com.botem.domain.account.entity.Bank
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.global.exception.BusinessException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDateTime
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FundingStateTransitionMatrixTest {

    @Test
    fun `일반 펀딩은 마감부터 선물 인증 완료까지 정의된 순서로 전이한다`() {
        val funding = birthday(deadline = NOW.minusDays(1))

        funding.closeContributions()
        assertEquals(FundingStatus.CLOSED, funding.status)

        funding.openResult(NOW)
        assertEquals(FundingStatus.GIFT_PREPARING, funding.status)

        funding.chooseGift()
        assertEquals(FundingStatus.WAITING_GIFT_VERIFICATION, funding.status)

        funding.completeGiftVerification(NOW)
        assertEquals(FundingStatus.COMPLETED, funding.status)
    }

    @Test
    fun `서프라이즈는 조기 마감부터 공개와 선물 인증 완료까지 정의된 순서로 전이한다`() {
        val funding = surprise(deadline = NOW.plusDays(3), revealAt = NOW)

        funding.closeEarly(NOW)
        assertEquals(FundingStatus.GIFT_PREPARING, funding.status)

        funding.prepareSurpriseGift(NOW.plusMinutes(1))
        assertEquals(FundingStatus.WAITING_REVEAL, funding.status)

        funding.revealSurprise(RECIPIENT_ID, NOW.plusMinutes(1))
        assertEquals(FundingStatus.REVEALED, funding.status)

        funding.beginGiftVerification()
        assertEquals(FundingStatus.WAITING_GIFT_VERIFICATION, funding.status)

        funding.completeGiftVerification(NOW.plusMinutes(1))
        assertEquals(FundingStatus.COMPLETED, funding.status)
    }

    @ParameterizedTest(name = "{0} 상태의 일반 펀딩은 결과를 열 수 있다")
    @MethodSource("resultOpenableStatuses")
    fun `일반 펀딩 결과 열기는 FUNDING과 CLOSED에서 시작한다`(status: FundingStatus) {
        val funding = birthday(deadline = NOW.minusDays(1)).also { setStatus(it, status) }

        funding.openResult(NOW)

        assertEquals(FundingStatus.GIFT_PREPARING, funding.status)
    }

    @ParameterizedTest(name = "{0} 상태의 서프라이즈는 선물 준비를 완료할 수 있다")
    @MethodSource("surpriseGiftPreparableStatuses")
    fun `서프라이즈 선물 준비는 정의된 이전 상태에서만 공개 대기로 전이한다`(status: FundingStatus) {
        val funding = surprise(deadline = NOW.minusDays(1), revealAt = NOW.plusDays(1)).also { setStatus(it, status) }

        funding.prepareSurpriseGift(NOW)

        assertEquals(FundingStatus.WAITING_REVEAL, funding.status)
    }

    @Test
    fun `허용되지 않은 전이는 상태와 전이 시각을 남기지 않는다`() {
        val completed = birthday(deadline = NOW.plusDays(1)).also { setStatus(it, FundingStatus.COMPLETED) }
        val earlyCloseException = assertThrows<BusinessException> { completed.closeEarly(NOW) }

        assertEquals(FundingErrorCode.FUNDING_EARLY_CLOSE_NOT_AVAILABLE, earlyCloseException.errorCode)
        assertEquals(FundingStatus.COMPLETED, completed.status)
        assertNull(completed.earlyClosedAt)

        val cancelled = birthday(deadline = NOW.minusDays(1)).also { setStatus(it, FundingStatus.CANCELLED) }
        val resultOpenException = assertThrows<BusinessException> { cancelled.openResult(NOW) }

        assertEquals(FundingErrorCode.FUNDING_RESULT_NOT_OPENABLE, resultOpenException.errorCode)
        assertEquals(FundingStatus.CANCELLED, cancelled.status)
        assertNull(cancelled.resultOpenedAt)

        val alreadyRevealed = surprise(deadline = NOW.minusDays(1), revealAt = NOW.minusMinutes(1))
            .also { setStatus(it, FundingStatus.REVEALED) }
        val revealException = assertThrows<BusinessException> { alreadyRevealed.revealSurprise(RECIPIENT_ID, NOW) }

        assertEquals(FundingErrorCode.SURPRISE_REVEAL_NOT_OPENABLE, revealException.errorCode)
        assertEquals(FundingStatus.REVEALED, alreadyRevealed.status)
        assertNull(alreadyRevealed.surpriseOpenedAt)
    }

    private fun birthday(deadline: LocalDateTime): Funding =
        funding(type = FundingType.BIRTHDAY, deadline = deadline, revealAt = null)

    private fun surprise(deadline: LocalDateTime, revealAt: LocalDateTime): Funding =
        funding(type = FundingType.SURPRISE, deadline = deadline, revealAt = revealAt)

    private fun funding(
        type: FundingType,
        deadline: LocalDateTime,
        revealAt: LocalDateTime?,
    ): Funding =
        Funding(
            ownerId = OWNER_ID,
            recipientId = if (type == FundingType.SURPRISE) RECIPIENT_ID else null,
            type = type,
            title = null,
            targetAmount = 100_000L,
            deadline = deadline,
            revealAt = revealAt,
            story = null,
            promise = null,
            product = FundingProductSnapshot(
                sourceWishId = null,
                name = "무선 헤드폰",
                imageUrl = "https://image.example.com/product.jpg",
                price = 100_000L,
                url = "https://shop.example.com/product",
            ),
            payoutAccount = FundingPayoutAccountSnapshot(
                sourcePayoutAccountId = null,
                bank = Bank.KAKAO_BANK,
                encryptedAccountNumber = "encrypted-account-number",
                accountHolder = "보탬",
            ),
        )

    private fun setStatus(funding: Funding, status: FundingStatus) {
        Funding::class.java.getDeclaredField("status").apply {
            isAccessible = true
            set(funding, status)
        }
    }

    companion object {
        private const val OWNER_ID = 1L
        private const val RECIPIENT_ID = 2L
        private val NOW = LocalDateTime.of(2026, 11, 15, 12, 0)

        @JvmStatic
        fun resultOpenableStatuses(): Stream<FundingStatus> =
            Stream.of(FundingStatus.FUNDING, FundingStatus.CLOSED)

        @JvmStatic
        fun surpriseGiftPreparableStatuses(): Stream<FundingStatus> =
            Stream.of(FundingStatus.FUNDING, FundingStatus.CLOSED, FundingStatus.GIFT_PREPARING)
    }
}
