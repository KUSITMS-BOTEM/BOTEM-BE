package com.botem.domain.home.service

import com.botem.domain.account.entity.Bank
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.projection.FundingContributionSummary
import com.botem.domain.home.dto.response.HomeFundingCardType
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeFundingServiceTest {

    private val fundingRepository = mock(FundingRepository::class.java)
    private val contributionRepository = mock(ContributionRepository::class.java)
    private val service = HomeFundingService(
        fundingRepository = fundingRepository,
        contributionRepository = contributionRepository,
        clock = Clock.fixed(Instant.parse("2026-11-08T03:00:00Z"), ZoneId.of("Asia/Seoul")),
    )

    @Test
    fun `마감 카드를 먼저 반환하고 진행 중 카드는 임박 순으로 정렬한다`() {
        val closed = funding(
            id = 1L,
            type = FundingType.BIRTHDAY,
            status = FundingStatus.CLOSED,
            deadline = LocalDateTime.of(2026, 11, 7, 23, 59),
        )
        val birthday = funding(
            id = 2L,
            type = FundingType.BIRTHDAY,
            deadline = LocalDateTime.of(2026, 11, 11, 23, 59),
        )
        val surprise = funding(
            id = 3L,
            type = FundingType.SURPRISE,
            status = FundingStatus.WAITING_REVEAL,
            deadline = LocalDateTime.of(2026, 11, 9, 23, 59),
            revealAt = LocalDateTime.of(2026, 11, 11, 0, 0),
        )
        given(fundingRepository.findAllByOwnerIdAndStatusIn(USER_ID, CANDIDATE_STATUSES))
            .willReturn(listOf(birthday, closed, surprise))
        given(contributionRepository.summarizeByFundingIds(listOf(2L, 1L, 3L)))
            .willReturn(
                listOf(
                    summary(fundingId = 1L, confirmedAmount = 100_000L),
                    summary(fundingId = 2L, confirmedAmount = 30_000L),
                    summary(fundingId = 3L, confirmedAmount = 50_000L),
                ),
            )

        val response = service.getHomeFundings(USER_ID)

        assertEquals(listOf(1L, 3L, 2L), response.fundingCards.map { it.fundingId })
        assertEquals(HomeFundingCardType.CLOSED_UNOPENED, response.fundingCards[0].cardType)
        assertEquals(HomeFundingCardType.IN_PROGRESS, response.fundingCards[1].cardType)
        assertEquals(3L, response.fundingCards[1].remainingDays)
        assertEquals(3L, response.fundingCards[2].remainingDays)
        assertEquals(2, response.activeFundingCount)
        assertTrue(response.canOpenFunding)
    }

    @Test
    fun `마감된 생일 펀딩은 상태 전환과 목표 달성 여부에 관계없이 미열람 카드로 반환한다`() {
        val fundingStatusNotChanged = funding(
            id = 1L,
            type = FundingType.BIRTHDAY,
            status = FundingStatus.FUNDING,
            deadline = LocalDateTime.of(2026, 11, 7, 23, 59),
        )
        val targetNotReached = funding(
            id = 2L,
            type = FundingType.BIRTHDAY,
            status = FundingStatus.CLOSED,
            deadline = LocalDateTime.of(2026, 11, 7, 23, 59),
        )
        given(fundingRepository.findAllByOwnerIdAndStatusIn(USER_ID, CANDIDATE_STATUSES))
            .willReturn(listOf(fundingStatusNotChanged, targetNotReached))
        given(contributionRepository.summarizeByFundingIds(listOf(1L, 2L)))
            .willReturn(
                listOf(
                    summary(fundingId = 1L, confirmedAmount = 100_000L),
                    summary(fundingId = 2L, confirmedAmount = 50_000L),
                ),
            )

        val response = service.getHomeFundings(USER_ID)

        assertEquals(listOf(1L, 2L), response.fundingCards.map { it.fundingId })
        assertTrue(response.fundingCards.all { it.cardType == HomeFundingCardType.CLOSED_UNOPENED })
        assertEquals(50, response.fundingCards[1].confirmedPercentage)
        assertEquals(0, response.activeFundingCount)
    }

    @Test
    fun `목표 금액과 참여자 집계 및 달성률을 반환하고 진행 중 펀딩이 다섯 개면 개설을 제한한다`() {
        val fundings = (1L..5L).map { id ->
            funding(
                id = id,
                type = FundingType.BIRTHDAY,
                deadline = LocalDateTime.of(2026, 11, 8 + id.toInt(), 23, 59),
            )
        }
        given(fundingRepository.findAllByOwnerIdAndStatusIn(USER_ID, CANDIDATE_STATUSES))
            .willReturn(fundings)
        given(contributionRepository.summarizeByFundingIds(fundings.map(Funding::id)))
            .willReturn(
                listOf(
                    summary(
                        fundingId = 1L,
                        confirmedAmount = 55_000L,
                        pendingAmount = 15_000L,
                        pendingCount = 2L,
                        participantCount = 7L,
                    ),
                ),
            )

        val response = service.getHomeFundings(USER_ID)
        val firstCard = response.fundingCards.first { it.fundingId == 1L }

        assertEquals(100_000L, firstCard.targetAmount)
        assertEquals(7L, firstCard.participantCount)
        assertEquals(55, firstCard.confirmedPercentage)
        assertEquals(15, firstCard.pendingPercentage)
        assertEquals(2L, firstCard.pendingContributionCount)
        assertEquals(5, response.activeFundingCount)
        assertFalse(response.canOpenFunding)
    }

    @Test
    fun `조회할 펀딩이 없으면 참여 집계 없이 빈 목록과 개설 가능 상태를 반환한다`() {
        given(fundingRepository.findAllByOwnerIdAndStatusIn(USER_ID, CANDIDATE_STATUSES))
            .willReturn(emptyList())

        val response = service.getHomeFundings(USER_ID)

        assertTrue(response.fundingCards.isEmpty())
        assertEquals(0, response.activeFundingCount)
        assertTrue(response.canOpenFunding)
        verifyNoInteractions(contributionRepository)
    }

    private fun funding(
        id: Long,
        type: FundingType,
        status: FundingStatus = FundingStatus.FUNDING,
        deadline: LocalDateTime,
        revealAt: LocalDateTime? = null,
    ): Funding =
        Funding(
            ownerId = USER_ID,
            recipientId = if (type == FundingType.SURPRISE) 2L else null,
            type = type,
            title = "생일 선물 펀딩",
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
                accountHolder = "지유",
            ),
        ).also { funding ->
            setField(funding, "id", id)
            setField(funding, "status", status)
        }

    private fun summary(
        fundingId: Long,
        confirmedAmount: Long = 0L,
        pendingAmount: Long = 0L,
        pendingCount: Long = 0L,
        participantCount: Long = 0L,
    ): FundingContributionSummary =
        FundingContributionSummary(
            fundingId = fundingId,
            confirmedAmount = confirmedAmount,
            pendingAmount = pendingAmount,
            pendingCount = pendingCount,
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
        private val CANDIDATE_STATUSES = setOf(
            FundingStatus.FUNDING,
            FundingStatus.CLOSED,
            FundingStatus.GIFT_PREPARING,
            FundingStatus.WAITING_REVEAL,
        )
    }
}
