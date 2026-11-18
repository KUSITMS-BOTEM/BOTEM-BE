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

class FundingExtensionServiceTest {

    private val fundingRepository = mock(FundingRepository::class.java)
    private val contributionRepository = mock(ContributionRepository::class.java)
    private val service = FundingExtensionService(
        fundingRepository = fundingRepository,
        contributionRepository = contributionRepository,
        clock = Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), ZoneId.of("Asia/Seoul")),
    )

    @Test
    fun `개설자가 목표 미달 펀딩을 연장하면 잠금 안에서 확정 금액을 확인하고 결과를 반환한다`() {
        val funding = funding()
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)
        given(
            contributionRepository.sumAmountByFundingIdAndStatus(FUNDING_ID, ContributionStatus.CONFIRMED),
        ).willReturn(80_000L)

        val response = service.extend(OWNER_ID, FUNDING_ID, NEW_DEADLINE)

        assertEquals(FUNDING_ID, response.fundingId)
        assertEquals(FundingStatus.FUNDING, response.status)
        assertEquals(NEW_DEADLINE, response.deadline)
        assertEquals(1, response.extensionCount)
        assertEquals(NOW, response.extendedAt)
        verify(fundingRepository).findByIdForUpdate(FUNDING_ID)
        verify(contributionRepository).sumAmountByFundingIdAndStatus(FUNDING_ID, ContributionStatus.CONFIRMED)
    }

    @Test
    fun `펀딩이 없으면 연장을 거부한다`() {
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(null)

        val exception = assertFailsWith<BusinessException> {
            service.extend(OWNER_ID, FUNDING_ID, NEW_DEADLINE)
        }

        assertEquals(FundingErrorCode.FUNDING_NOT_FOUND, exception.errorCode)
        verify(contributionRepository, never())
            .sumAmountByFundingIdAndStatus(FUNDING_ID, ContributionStatus.CONFIRMED)
    }

    @Test
    fun `개설자가 아니면 확정 금액을 조회하지 않고 연장을 거부한다`() {
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding())

        val exception = assertFailsWith<BusinessException> {
            service.extend(OTHER_USER_ID, FUNDING_ID, NEW_DEADLINE)
        }

        assertEquals(FundingErrorCode.FUNDING_OWNER_ONLY, exception.errorCode)
        verify(contributionRepository, never())
            .sumAmountByFundingIdAndStatus(FUNDING_ID, ContributionStatus.CONFIRMED)
    }

    @Test
    fun `확정 금액이 목표에 도달했으면 연장을 거부한다`() {
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding())
        given(
            contributionRepository.sumAmountByFundingIdAndStatus(FUNDING_ID, ContributionStatus.CONFIRMED),
        ).willReturn(100_000L)

        val exception = assertFailsWith<BusinessException> {
            service.extend(OWNER_ID, FUNDING_ID, NEW_DEADLINE)
        }

        assertEquals(FundingErrorCode.FUNDING_TARGET_REACHED, exception.errorCode)
    }

    private fun funding(): Funding =
        Funding(
            ownerId = OWNER_ID,
            recipientId = null,
            type = FundingType.BIRTHDAY,
            title = "생일 선물 펀딩",
            targetAmount = 100_000L,
            deadline = NOW.minusDays(1),
            revealAt = null,
            story = "새 헤드폰이 필요해요",
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
        ).also {
            setField(it, "id", FUNDING_ID)
            setField(it, "status", FundingStatus.CLOSED)
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
        private val NOW = LocalDateTime.of(2026, 11, 18, 12, 0)
        private val NEW_DEADLINE = LocalDateTime.of(2026, 11, 30, 23, 59, 59, 999_999_000)
    }
}
