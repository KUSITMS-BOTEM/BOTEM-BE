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
import com.botem.domain.funding.repository.projection.FundingContributionSummary
import com.botem.global.exception.BusinessException
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.mock
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FundingResultServiceTest {

    private val fundingRepository = mock(FundingRepository::class.java)
    private val contributionRepository = mock(ContributionRepository::class.java)
    private val service = FundingResultService(
        fundingRepository = fundingRepository,
        contributionRepository = contributionRepository,
        clock = Clock.fixed(Instant.parse("2026-11-14T15:00:00Z"), ZoneId.of("Asia/Seoul")),
    )

    @Test
    fun `개설자가 마감된 생일 펀딩 결과를 조회한다`() {
        val funding = funding()
        given(fundingRepository.findById(FUNDING_ID)).willReturn(Optional.of(funding))
        given(contributionRepository.summarizeByFundingIds(listOf(FUNDING_ID)))
            .willReturn(listOf(summary(confirmedAmount = 80_000L, participantCount = 4L)))

        val result = service.getResult(USER_ID, FUNDING_ID)

        assertEquals(FUNDING_ID, result.fundingId)
        assertEquals(100_000L, result.targetAmount)
        assertEquals(80_000L, result.confirmedAmount)
        assertEquals(80, result.achievementPercentage)
        assertFalse(result.targetReached)
        assertEquals(4L, result.participantCount)
        assertEquals(null, result.resultOpenedAt)
    }

    @Test
    fun `다른 사용자의 펀딩 결과 조회를 거부한다`() {
        val funding = funding()
        given(fundingRepository.findById(FUNDING_ID)).willReturn(Optional.of(funding))

        val exception = assertFailsWith<BusinessException> {
            service.getResult(USER_ID + 1, FUNDING_ID)
        }

        assertEquals(FundingErrorCode.FUNDING_OWNER_ONLY, exception.errorCode)
        verify(contributionRepository, never()).summarizeByFundingIds(listOf(FUNDING_ID))
    }

    @Test
    fun `존재하지 않는 펀딩 결과 조회를 거부한다`() {
        given(fundingRepository.findById(FUNDING_ID)).willReturn(Optional.empty())

        val exception = assertFailsWith<BusinessException> {
            service.getResult(USER_ID, FUNDING_ID)
        }

        assertEquals(FundingErrorCode.FUNDING_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `참여 내역이 없는 펀딩 결과는 금액과 참여자 수를 0으로 반환한다`() {
        val funding = funding()
        given(fundingRepository.findById(FUNDING_ID)).willReturn(Optional.of(funding))
        given(contributionRepository.summarizeByFundingIds(listOf(FUNDING_ID))).willReturn(emptyList())

        val result = service.getResult(USER_ID, FUNDING_ID)

        assertEquals(0L, result.confirmedAmount)
        assertEquals(0, result.achievementPercentage)
        assertFalse(result.targetReached)
        assertEquals(0L, result.participantCount)
    }

    @Test
    fun `서프라이즈 펀딩 결과 조회를 거부한다`() {
        val funding = funding(type = FundingType.SURPRISE)
        given(fundingRepository.findById(FUNDING_ID)).willReturn(Optional.of(funding))

        val exception = assertFailsWith<BusinessException> {
            service.getResult(USER_ID, FUNDING_ID)
        }

        assertEquals(FundingErrorCode.BIRTHDAY_FUNDING_REQUIRED, exception.errorCode)
    }

    @Test
    fun `마감 전 펀딩 결과 조회를 거부한다`() {
        val funding = funding(deadline = NOW.plusMinutes(1))
        given(fundingRepository.findById(FUNDING_ID)).willReturn(Optional.of(funding))

        val exception = assertFailsWith<BusinessException> {
            service.getResult(USER_ID, FUNDING_ID)
        }

        assertEquals(FundingErrorCode.FUNDING_NOT_ENDED, exception.errorCode)
    }

    @Test
    fun `결과 조회가 허용되지 않은 펀딩 상태를 거부한다`() {
        val funding = funding(status = FundingStatus.WAITING_REVEAL)
        given(fundingRepository.findById(FUNDING_ID)).willReturn(Optional.of(funding))

        val exception = assertFailsWith<BusinessException> {
            service.getResult(USER_ID, FUNDING_ID)
        }

        assertEquals(FundingErrorCode.FUNDING_RESULT_NOT_OPENABLE, exception.errorCode)
    }

    @Test
    fun `확인 대기 참여가 없으면 펀딩 결과를 열고 최신 결과를 반환한다`() {
        val funding = funding()
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)
        given(
            contributionRepository.existsByFundingIdAndStatus(
                FUNDING_ID,
                ContributionStatus.PENDING_CONFIRMATION,
            ),
        ).willReturn(false)
        given(contributionRepository.summarizeByFundingIds(listOf(FUNDING_ID)))
            .willReturn(listOf(summary(confirmedAmount = 120_000L, participantCount = 5L)))

        val result = service.openResult(USER_ID, FUNDING_ID)

        assertEquals(NOW, result.resultOpenedAt)
        assertEquals(FundingStatus.GIFT_PREPARING, funding.status)
        assertEquals(120, result.achievementPercentage)
        assertTrue(result.targetReached)
    }

    @Test
    fun `참여 내역이 없는 펀딩도 결과를 열 수 있다`() {
        val funding = funding()
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)
        given(
            contributionRepository.existsByFundingIdAndStatus(
                FUNDING_ID,
                ContributionStatus.PENDING_CONFIRMATION,
            ),
        ).willReturn(false)
        given(contributionRepository.summarizeByFundingIds(listOf(FUNDING_ID))).willReturn(emptyList())

        val result = service.openResult(USER_ID, FUNDING_ID)

        assertEquals(NOW, result.resultOpenedAt)
        assertEquals(0L, result.confirmedAmount)
        assertEquals(0, result.achievementPercentage)
        assertEquals(0L, result.participantCount)
    }

    @Test
    fun `존재하지 않는 펀딩 결과 열기를 거부한다`() {
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(null)

        val exception = assertFailsWith<BusinessException> {
            service.openResult(USER_ID, FUNDING_ID)
        }

        assertEquals(FundingErrorCode.FUNDING_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `확인 대기 참여가 있으면 펀딩 결과를 열 수 없다`() {
        val funding = funding()
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)
        given(
            contributionRepository.existsByFundingIdAndStatus(
                FUNDING_ID,
                ContributionStatus.PENDING_CONFIRMATION,
            ),
        ).willReturn(true)

        val exception = assertFailsWith<BusinessException> {
            service.openResult(USER_ID, FUNDING_ID)
        }

        assertEquals(FundingErrorCode.PENDING_CONTRIBUTION_EXISTS, exception.errorCode)
        assertEquals(null, funding.resultOpenedAt)
        assertEquals(FundingStatus.CLOSED, funding.status)
        verify(contributionRepository, never()).summarizeByFundingIds(listOf(FUNDING_ID))
    }

    @Test
    fun `이미 열린 펀딩 결과 재요청은 확인 대기 검사를 반복하지 않고 기존 결과를 반환한다`() {
        val funding = funding()
        val firstOpenedAt = NOW.minusMinutes(10)
        funding.openResult(firstOpenedAt)
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)
        given(contributionRepository.summarizeByFundingIds(listOf(FUNDING_ID)))
            .willReturn(listOf(summary(confirmedAmount = 100_000L, participantCount = 3L)))

        val result = service.openResult(USER_ID, FUNDING_ID)

        assertEquals(firstOpenedAt, result.resultOpenedAt)
        verify(contributionRepository, never()).existsByFundingIdAndStatus(
            FUNDING_ID,
            ContributionStatus.PENDING_CONFIRMATION,
        )
    }

    private fun funding(
        type: FundingType = FundingType.BIRTHDAY,
        deadline: LocalDateTime = NOW.minusHours(1),
        status: FundingStatus = FundingStatus.CLOSED,
    ): Funding =
        Funding(
            ownerId = USER_ID,
            recipientId = if (type == FundingType.SURPRISE) 2L else null,
            type = type,
            title = "생일 선물 펀딩",
            targetAmount = 100_000L,
            deadline = deadline,
            revealAt = if (type == FundingType.SURPRISE) NOW.minusMinutes(1) else null,
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
        ).also { funding ->
            setField(funding, "id", FUNDING_ID)
            setField(funding, "status", status)
        }

    private fun summary(
        confirmedAmount: Long,
        participantCount: Long,
    ): FundingContributionSummary =
        FundingContributionSummary(
            fundingId = FUNDING_ID,
            confirmedAmount = confirmedAmount,
            pendingAmount = 0L,
            pendingCount = 0L,
            participantCount = participantCount,
        )

    private fun setField(target: Any, fieldName: String, value: Any) {
        target.javaClass.getDeclaredField(fieldName).apply {
            isAccessible = true
            set(target, value)
        }
    }

    companion object {
        private const val USER_ID = 1L
        private const val FUNDING_ID = 12L
        private val NOW = LocalDateTime.of(2026, 11, 15, 0, 0)
    }
}
