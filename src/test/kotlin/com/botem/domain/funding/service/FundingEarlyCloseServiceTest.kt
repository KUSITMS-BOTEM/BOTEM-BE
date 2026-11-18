package com.botem.domain.funding.service

import com.botem.domain.account.entity.Bank
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.global.exception.BusinessException
import org.junit.jupiter.api.Test
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

class FundingEarlyCloseServiceTest {

    private val fundingRepository = mock(FundingRepository::class.java)
    private val contributionRepository = mock(ContributionRepository::class.java)
    private val service = FundingEarlyCloseService(
        fundingRepository = fundingRepository,
        fundingContributionCloser = FundingContributionCloser(contributionRepository),
        clock = Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), ZoneId.of("Asia/Seoul")),
    )

    @Test
    fun `개설자가 일반 펀딩을 조기 마감하면 송금 전 보탬을 메시지 전용으로 정리한다`() {
        val funding = funding()
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)

        val response = service.close(OWNER_ID, FUNDING_ID)

        assertEquals(FundingStatus.CLOSED, response.status)
        assertEquals(NOW, response.earlyClosedAt)
        verify(contributionRepository).convertAwaitingTransfersToMessageOnly(FUNDING_ID, NOW)
    }

    @Test
    fun `서프라이즈 조기 마감은 공개 시각을 유지하고 공개 대기 상태를 반환한다`() {
        val funding = funding(type = FundingType.SURPRISE)
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)

        val response = service.close(OWNER_ID, FUNDING_ID)

        assertEquals(FundingStatus.WAITING_REVEAL, response.status)
        assertEquals(REVEAL_AT, response.revealAt)
    }

    @Test
    fun `조기 마감 재요청은 송금 전 보탬 정리를 반복하지 않는다`() {
        val funding = funding().also { it.closeEarly(NOW.minusMinutes(1)) }
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)

        val response = service.close(OWNER_ID, FUNDING_ID)

        assertEquals(NOW.minusMinutes(1), response.earlyClosedAt)
        verify(contributionRepository, never()).convertAwaitingTransfersToMessageOnly(FUNDING_ID, NOW)
    }

    @Test
    fun `펀딩이 없거나 개설자가 아니면 조기 마감을 거부한다`() {
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(null)
        assertEquals(
            FundingErrorCode.FUNDING_NOT_FOUND,
            assertFailsWith<BusinessException> { service.close(OWNER_ID, FUNDING_ID) }.errorCode,
        )

        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding())
        assertEquals(
            FundingErrorCode.FUNDING_OWNER_ONLY,
            assertFailsWith<BusinessException> { service.close(OTHER_USER_ID, FUNDING_ID) }.errorCode,
        )
    }

    private fun funding(type: FundingType = FundingType.BIRTHDAY): Funding =
        Funding(
            ownerId = OWNER_ID,
            recipientId = if (type == FundingType.SURPRISE) 3L else null,
            type = type,
            title = "생일 선물 펀딩",
            targetAmount = 100_000L,
            deadline = NOW.plusDays(3),
            revealAt = if (type == FundingType.SURPRISE) REVEAL_AT else null,
            story = "새 헤드폰이 필요해요",
            promise = null,
            product = FundingProductSnapshot(1L, "무선 헤드폰", "https://image.example.com/product.jpg", 100_000L, "https://shop.example.com/product"),
            payoutAccount = FundingPayoutAccountSnapshot(1L, Bank.KAKAO_BANK, "encrypted", "지유"),
        ).also { setField(it, "id", FUNDING_ID) }

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
        private val NOW = LocalDateTime.of(2026, 11, 18, 12, 0)
        private val REVEAL_AT = NOW.plusDays(5)
    }
}
