package com.botem.domain.funding.service

import com.botem.domain.account.entity.Bank
import com.botem.domain.funding.entity.ContributionStatus
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
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FundingEarlyCloseServiceTest {

    private val fundingRepository = mock(FundingRepository::class.java)
    private val contributionRepository = mock(ContributionRepository::class.java)
    private val service = FundingEarlyCloseService(
        fundingRepository = fundingRepository,
        contributionRepository = contributionRepository,
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
        verify(contributionRepository).convertAwaitingTransfersToMessageOnly(FUNDING_ID, NOW.minusHours(9))
    }

    @Test
    fun `서프라이즈 조기 마감은 공개 시각을 유지하고 선물 준비 상태를 반환한다`() {
        val funding = funding(type = FundingType.SURPRISE)
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)

        val response = service.close(OWNER_ID, FUNDING_ID)

        assertEquals(FundingStatus.GIFT_PREPARING, response.status)
        assertEquals(REVEAL_AT, response.revealAt)
    }

    @Test
    fun `조기 마감 후 참여자가 있으면 재요청에도 기존 조기 마감 결과를 반환한다`() {
        val funding = funding().also { it.closeEarly(NOW.minusMinutes(1)) }
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)
        given(contributionRepository.existsByFundingIdAndStatusNot(FUNDING_ID, ContributionStatus.CANCELLED)).willReturn(true)

        val response = service.close(OWNER_ID, FUNDING_ID)

        assertEquals(FundingStatus.CLOSED, response.status)
        assertEquals(NOW.minusMinutes(1), response.earlyClosedAt)
        verify(contributionRepository).existsByFundingIdAndStatusNot(FUNDING_ID, ContributionStatus.CANCELLED)
        verify(contributionRepository, never()).convertAwaitingTransfersToMessageOnly(anyLong(), anyDateTime())
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

    @Test
    fun `취소된 펀딩은 찾을 수 없는 것으로 처리한다`() {
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(
            funding(status = FundingStatus.CANCELLED),
        )

        val exception = assertFailsWith<BusinessException> {
            service.close(OWNER_ID, FUNDING_ID)
        }

        assertEquals(FundingErrorCode.FUNDING_NOT_FOUND, exception.errorCode)
        verify(contributionRepository, never()).convertAwaitingTransfersToMessageOnly(anyLong(), anyDateTime())
    }

    @Test
    fun `마감일이 지났고 참여자가 없는 생일 펀딩은 취소 상태로 종료하고 재요청에도 같은 결과를 준다`() {
        val funding = funding(deadline = NOW.minusDays(1), status = FundingStatus.CLOSED)
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)
        given(contributionRepository.existsByFundingIdAndStatusNot(FUNDING_ID, ContributionStatus.CANCELLED)).willReturn(false)

        val first = service.close(OWNER_ID, FUNDING_ID)
        val second = service.close(OWNER_ID, FUNDING_ID)

        assertEquals(FundingStatus.CANCELLED, first.status)
        assertNull(first.earlyClosedAt)
        assertEquals(first, second)
        verify(contributionRepository, never()).convertAwaitingTransfersToMessageOnly(anyLong(), anyDateTime())
    }

    @Test
    fun `조기 마감된 참여자 0명 생일 펀딩은 마감일 전에도 취소 상태로 종료하고 재요청에도 같은 결과를 준다`() {
        val earlyClosedAt = NOW.minusMinutes(1)
        val funding = funding().also { it.closeEarly(earlyClosedAt) }
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)
        given(contributionRepository.existsByFundingIdAndStatusNot(FUNDING_ID, ContributionStatus.CANCELLED)).willReturn(false)

        val first = service.close(OWNER_ID, FUNDING_ID)
        val second = service.close(OWNER_ID, FUNDING_ID)

        assertEquals(FundingStatus.CANCELLED, first.status)
        assertEquals(earlyClosedAt, first.earlyClosedAt)
        assertEquals(first, second)
        verify(contributionRepository, times(1)).existsByFundingIdAndStatusNot(FUNDING_ID, ContributionStatus.CANCELLED)
        verify(contributionRepository, never()).convertAwaitingTransfersToMessageOnly(anyLong(), anyDateTime())
    }

    @Test
    fun `마감일이 지난 펀딩에 참여자가 있으면 종료를 거부한다`() {
        val funding = funding(deadline = NOW.minusDays(1), status = FundingStatus.CLOSED)
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)
        given(contributionRepository.existsByFundingIdAndStatusNot(FUNDING_ID, ContributionStatus.CANCELLED)).willReturn(true)

        val exception = assertFailsWith<BusinessException> { service.close(OWNER_ID, FUNDING_ID) }

        assertEquals(FundingErrorCode.FUNDING_END_NOT_AVAILABLE, exception.errorCode)
        assertEquals(FundingStatus.CLOSED, funding.status)
    }

    @Test
    fun `참여자 0명으로 종료한 펀딩도 개설자가 아니면 거부한다`() {
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(
            funding(deadline = NOW.minusDays(1), status = FundingStatus.CANCELLED),
        )

        val exception = assertFailsWith<BusinessException> { service.close(OTHER_USER_ID, FUNDING_ID) }

        assertEquals(FundingErrorCode.FUNDING_OWNER_ONLY, exception.errorCode)
    }

    private fun funding(
        type: FundingType = FundingType.BIRTHDAY,
        status: FundingStatus = FundingStatus.FUNDING,
        deadline: LocalDateTime = NOW.plusDays(3),
    ): Funding =
        Funding(
            ownerId = OWNER_ID,
            recipientId = if (type == FundingType.SURPRISE) 3L else null,
            type = type,
            title = "생일 선물 펀딩",
            targetAmount = 100_000L,
            deadline = deadline,
            revealAt = if (type == FundingType.SURPRISE) REVEAL_AT else null,
            story = "새 헤드폰이 필요해요",
            promise = null,
            product = FundingProductSnapshot(1L, "무선 헤드폰", "https://image.example.com/product.jpg", 100_000L, "https://shop.example.com/product"),
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

    // Kotlin의 null 불가 파라미터에도 쓸 수 있도록, 시각 매처를 등록하고 null 대신 임의 값을 넘긴다
    private fun anyDateTime(): LocalDateTime = ArgumentMatchers.any(LocalDateTime::class.java) ?: LocalDateTime.MIN

    companion object {
        private const val OWNER_ID = 1L
        private const val OTHER_USER_ID = 2L
        private const val FUNDING_ID = 12L
        private val NOW = LocalDateTime.of(2026, 11, 18, 12, 0)
        private val REVEAL_AT = NOW.plusDays(5)
    }
}
