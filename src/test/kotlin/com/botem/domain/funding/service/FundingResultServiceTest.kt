package com.botem.domain.funding.service

import com.botem.domain.account.entity.Bank
import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.entity.ContributionThankCard
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.entity.RollingPaperMessage
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.ContributionThankCardRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.RollingPaperMessageRepository
import com.botem.domain.funding.repository.projection.FundingContributionSummary
import com.botem.domain.user.entity.User
import com.botem.domain.user.repository.UserRepository
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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FundingResultServiceTest {

    private val fundingRepository = mock(FundingRepository::class.java)
    private val contributionRepository = mock(ContributionRepository::class.java)
    private val rollingPaperMessageRepository = mock(RollingPaperMessageRepository::class.java)
    private val contributionThankCardRepository = mock(ContributionThankCardRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val service = FundingResultService(
        fundingRepository = fundingRepository,
        contributionRepository = contributionRepository,
        rollingPaperMessageRepository = rollingPaperMessageRepository,
        contributionThankCardRepository = contributionThankCardRepository,
        userRepository = userRepository,
        fundingContributionCloser = FundingContributionCloser(contributionRepository),
        clock = Clock.fixed(Instant.parse("2026-11-14T15:00:00Z"), ZoneId.of("Asia/Seoul")),
    )

    @Test
    fun `개설자가 마감된 생일 펀딩 결과를 조회한다`() {
        val funding = funding()
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)
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
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)

        val exception = assertFailsWith<BusinessException> {
            service.getResult(USER_ID + 1, FUNDING_ID)
        }

        assertEquals(FundingErrorCode.FUNDING_OWNER_ONLY, exception.errorCode)
        verify(contributionRepository, never()).summarizeByFundingIds(listOf(FUNDING_ID))
    }

    @Test
    fun `존재하지 않는 펀딩 결과 조회를 거부한다`() {
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(null)

        val exception = assertFailsWith<BusinessException> {
            service.getResult(USER_ID, FUNDING_ID)
        }

        assertEquals(FundingErrorCode.FUNDING_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `참여 내역이 없는 펀딩 결과는 금액과 참여자 수를 0으로 반환한다`() {
        val funding = funding()
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)
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
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)

        val exception = assertFailsWith<BusinessException> {
            service.getResult(USER_ID, FUNDING_ID)
        }

        assertEquals(FundingErrorCode.BIRTHDAY_FUNDING_REQUIRED, exception.errorCode)
    }

    @Test
    fun `마감 전 펀딩 결과 조회를 거부한다`() {
        val funding = funding(deadline = NOW.plusMinutes(1))
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)

        val exception = assertFailsWith<BusinessException> {
            service.getResult(USER_ID, FUNDING_ID)
        }

        assertEquals(FundingErrorCode.FUNDING_NOT_ENDED, exception.errorCode)
    }

    @Test
    fun `결과 조회가 허용되지 않은 펀딩 상태를 거부한다`() {
        val funding = funding(status = FundingStatus.WAITING_REVEAL)
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)

        val exception = assertFailsWith<BusinessException> {
            service.getResult(USER_ID, FUNDING_ID)
        }

        assertEquals(FundingErrorCode.FUNDING_RESULT_NOT_OPENABLE, exception.errorCode)
    }

    @Test
    fun `확인 대기 참여가 없으면 펀딩 결과를 열고 최신 결과를 반환한다`() {
        val funding = funding()
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)
        given(contributionRepository.summarizeByFundingIds(listOf(FUNDING_ID)))
            .willReturn(listOf(summary(confirmedAmount = 120_000L, participantCount = 5L)))

        val result = service.openResult(USER_ID, FUNDING_ID, excludePendingContributions = false)

        assertEquals(NOW, result.resultOpenedAt)
        assertEquals(FundingStatus.GIFT_PREPARING, funding.status)
        assertEquals(120, result.achievementPercentage)
        assertTrue(result.targetReached)
    }

    @Test
    fun `참여 내역이 없는 펀딩도 결과를 열 수 있다`() {
        val funding = funding()
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)
        given(contributionRepository.summarizeByFundingIds(listOf(FUNDING_ID))).willReturn(emptyList())

        val result = service.openResult(USER_ID, FUNDING_ID, excludePendingContributions = false)

        assertEquals(NOW, result.resultOpenedAt)
        assertEquals(0L, result.confirmedAmount)
        assertEquals(0, result.achievementPercentage)
        assertEquals(0L, result.participantCount)
    }

    @Test
    fun `존재하지 않는 펀딩 결과 열기를 거부한다`() {
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(null)

        val exception = assertFailsWith<BusinessException> {
            service.openResult(USER_ID, FUNDING_ID, excludePendingContributions = false)
        }

        assertEquals(FundingErrorCode.FUNDING_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `확인 대기 참여가 있으면 펀딩 결과를 열 수 없다`() {
        val funding = funding()
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)
        given(contributionRepository.summarizeByFundingIds(listOf(FUNDING_ID)))
            .willReturn(listOf(summary(confirmedAmount = 50_000L, participantCount = 3L, pendingCount = 1L)))

        val exception = assertFailsWith<BusinessException> {
            service.openResult(USER_ID, FUNDING_ID, excludePendingContributions = false)
        }

        assertEquals(FundingErrorCode.PENDING_CONTRIBUTION_EXISTS, exception.errorCode)
        assertEquals(null, funding.resultOpenedAt)
        assertEquals(FundingStatus.CLOSED, funding.status)
    }

    @Test
    fun `이미 열린 펀딩 결과 재요청은 확인 대기 검사를 반복하지 않고 기존 결과를 반환한다`() {
        val funding = funding()
        val firstOpenedAt = NOW.minusMinutes(10)
        funding.openResult(firstOpenedAt)
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)
        given(contributionRepository.summarizeByFundingIds(listOf(FUNDING_ID)))
            .willReturn(listOf(summary(confirmedAmount = 100_000L, participantCount = 3L)))

        val result = service.openResult(USER_ID, FUNDING_ID, excludePendingContributions = false)

        assertEquals(firstOpenedAt, result.resultOpenedAt)
        verify(contributionRepository, never()).convertAwaitingTransfersToMessageOnly(FUNDING_ID, NOW)
    }

    @Test
    fun `이미 연 결과도 연장과 일관된 상태를 읽도록 펀딩을 잠그고 조회한다`() {
        val funding = funding().also { it.openResult(NOW.minusHours(1)) }
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)
        given(contributionRepository.summarizeByFundingIds(listOf(FUNDING_ID)))
            .willReturn(listOf(summary(confirmedAmount = 100_000L, participantCount = 3L)))

        val result = service.getResult(USER_ID, FUNDING_ID)

        assertEquals(NOW.minusHours(1), result.resultOpenedAt)
        verify(fundingRepository).findByIdForUpdate(FUNDING_ID)
        verify(contributionRepository, never()).convertAwaitingTransfersToMessageOnly(FUNDING_ID, NOW)
    }

    @Test
    fun `결과를 열기 전 조회하면 보태기를 마감하고 송금 전 참여를 메시지 전용으로 바꾼다`() {
        val funding = funding(status = FundingStatus.FUNDING)
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)
        given(contributionRepository.summarizeByFundingIds(listOf(FUNDING_ID)))
            .willReturn(listOf(summary(confirmedAmount = 0L, participantCount = 2L, pendingCount = 1L)))
        given(rollingPaperMessageRepository.countByFundingId(FUNDING_ID)).willReturn(2L)

        val result = service.getResult(USER_ID, FUNDING_ID)

        assertEquals(FundingStatus.CLOSED, funding.status)
        verify(contributionRepository).convertAwaitingTransfersToMessageOnly(FUNDING_ID, NOW)
        assertEquals(2L, result.messageCount)
        assertEquals(1L, result.pendingContributionCount)
        assertTrue(result.pendingCleanupRequired)
    }

    @Test
    fun `확인 대기를 결과에서 제외하겠다고 요청하면 확인 대기가 있어도 결과를 연다`() {
        val funding = funding()
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)
        given(contributionRepository.summarizeByFundingIds(listOf(FUNDING_ID)))
            .willReturn(listOf(summary(confirmedAmount = 50_000L, participantCount = 3L, pendingCount = 1L)))

        val result = service.openResult(USER_ID, FUNDING_ID, excludePendingContributions = true)

        assertEquals(NOW, result.resultOpenedAt)
        assertEquals(FundingStatus.GIFT_PREPARING, funding.status)
        assertEquals(50_000L, result.confirmedAmount)
        assertEquals(1L, result.pendingContributionCount)
        assertFalse(result.pendingCleanupRequired)
    }

    @Test
    fun `결과에 취소를 뺀 보탬마다 메시지 종류와 감사카드 발송 여부를 담는다`() {
        val funding = funding()
        val member = contribution(id = 21L, userId = MEMBER_ID, name = "서준", status = ContributionStatus.CONFIRMED)
        val guest = contribution(id = 22L, userId = null, name = "서아", status = ContributionStatus.MESSAGE_ONLY)
        val cancelled = contribution(id = 23L, userId = null, name = "도윤", status = ContributionStatus.CANCELLED)
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)
        given(contributionRepository.findAllByFundingIdOrderByCreatedAtAscIdAsc(FUNDING_ID))
            .willReturn(listOf(member, guest, cancelled))
        given(rollingPaperMessageRepository.findAllByContributionIdIn(listOf(21L, 22L))).willReturn(
            listOf(
                RollingPaperMessage(
                    contributionId = 21L,
                    content = "생일 축하해",
                    paperTemplateCode = "BLUE_DOT_GRID",
                    photoUrl = "https://image.example.com/photo.jpg",
                ),
                RollingPaperMessage(contributionId = 22L, content = "  ", paperTemplateCode = "BLUE_DOT_GRID", decorationCode = "CAKE"),
            ),
        )
        given(contributionThankCardRepository.findAllByContributionIdIn(listOf(21L, 22L))).willReturn(
            listOf(ContributionThankCard(contributionId = 21L, senderUserId = USER_ID, message = "고마워", sentAt = NOW)),
        )
        given(userRepository.findAllById(setOf(MEMBER_ID))).willReturn(
            listOf(
                User(kakaoId = 7L, nickname = "서준", profileImageUrl = "https://image.example.com/profile.jpg")
                    .also { setField(it, "id", MEMBER_ID) },
            ),
        )

        val contributions = service.getResult(USER_ID, FUNDING_ID).contributions

        assertEquals(listOf(21L, 22L), contributions.map { it.contributionId })
        val first = contributions[0]
        assertEquals("서준", first.name)
        assertEquals("https://image.example.com/profile.jpg", first.profileImageUrl)
        assertEquals(ContributionStatus.CONFIRMED, first.status)
        assertEquals(NOW.minusDays(3), first.participatedAt)
        assertTrue(first.hasMessage)
        assertTrue(first.hasPhoto)
        assertFalse(first.hasVoice)
        assertTrue(first.thankCardSent)
        val second = contributions[1]
        assertEquals(null, second.profileImageUrl)
        assertFalse(second.hasMessage)
        assertFalse(second.hasPhoto)
        assertFalse(second.thankCardSent)
    }

    private fun contribution(id: Long, userId: Long?, name: String, status: ContributionStatus): Contribution =
        Contribution(
            fundingId = FUNDING_ID,
            participantUserId = userId,
            participantName = name,
            amount = 10_000L,
            status = ContributionStatus.AWAITING_TRANSFER,
            guestPasswordHash = null,
        ).also {
            setField(it, "id", id)
            setField(it, "status", status)
            setField(it, "createdAt", NOW.minusDays(3))
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
        pendingCount: Long = 0L,
    ): FundingContributionSummary =
        FundingContributionSummary(
            fundingId = FUNDING_ID,
            confirmedAmount = confirmedAmount,
            pendingAmount = pendingCount * 10_000L,
            pendingCount = pendingCount,
            participantCount = participantCount,
        )

    // createdAt처럼 상위 클래스에 선언된 필드도 찾는다
    private fun setField(target: Any, fieldName: String, value: Any) {
        generateSequence<Class<*>>(target.javaClass) { it.superclass }
            .firstNotNullOf { type -> type.declaredFields.firstOrNull { it.name == fieldName } }
            .apply {
                isAccessible = true
                set(target, value)
            }
    }

    companion object {
        private const val USER_ID = 1L
        private const val FUNDING_ID = 12L
        private const val MEMBER_ID = 7L
        private val NOW = LocalDateTime.of(2026, 11, 15, 0, 0)
    }
}
