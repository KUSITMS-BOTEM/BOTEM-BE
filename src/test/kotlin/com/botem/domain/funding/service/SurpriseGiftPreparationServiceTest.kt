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
import com.botem.domain.funding.repository.RollingPaperMessageRepository
import com.botem.domain.funding.repository.projection.FundingContributionSummary
import com.botem.domain.user.entity.User
import com.botem.domain.user.repository.UserRepository
import com.botem.global.exception.BusinessException
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SurpriseGiftPreparationServiceTest {

    private val fundingRepository = mock(FundingRepository::class.java)
    private val contributionRepository = mock(ContributionRepository::class.java)
    private val rollingPaperMessageRepository = mock(RollingPaperMessageRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val service = SurpriseGiftPreparationService(
        fundingRepository = fundingRepository,
        contributionRepository = contributionRepository,
        rollingPaperMessageRepository = rollingPaperMessageRepository,
        userRepository = userRepository,
        fundingContributionCloser = FundingContributionCloser(contributionRepository),
        clock = Clock.fixed(Instant.parse("2026-11-14T15:00:00Z"), ZoneId.of("Asia/Seoul")),
    )

    @Test
    fun `주최자가 마감된 서프라이즈의 선물 준비 화면을 조회하면 보태기를 마감하고 확정 금액을 반환한다`() {
        val funding = funding(status = FundingStatus.FUNDING)
        givenFunding(funding)
        givenSummary(confirmedAmount = 80_000L, participantCount = 4L, pendingCount = 1L)
        given(rollingPaperMessageRepository.countByFundingId(FUNDING_ID)).willReturn(3L)

        val response = service.getPreparation(OWNER_ID, FUNDING_ID)

        assertEquals(FundingStatus.CLOSED, funding.status)
        verify(contributionRepository).convertAwaitingTransfersToMessageOnly(FUNDING_ID, NOW)
        assertEquals("지유", response.recipientName)
        assertFalse(response.recipientWithdrawn)
        assertEquals(80_000L, response.confirmedAmount)
        assertEquals(80, response.achievementPercentage)
        assertFalse(response.targetReached)
        assertEquals(4L, response.participantCount)
        assertEquals(3L, response.messageCount)
        assertTrue(response.pendingCleanupRequired)
        assertNull(response.giftPreparedAt)
    }

    @Test
    fun `목표 미달이어도 선물 준비를 완료하고 공개 대기 상태로 전환한다`() {
        val funding = funding()
        givenFunding(funding)
        givenSummary(confirmedAmount = 30_000L, participantCount = 2L)

        val response = service.prepare(OWNER_ID, FUNDING_ID, excludePendingContributions = false)

        assertEquals(FundingStatus.WAITING_REVEAL, funding.status)
        assertEquals(NOW, response.giftPreparedAt)
        assertEquals(30, response.achievementPercentage)
        assertFalse(response.pendingCleanupRequired)
    }

    @Test
    fun `확인 대기가 남아 있으면 제외를 선택해야 선물 준비를 완료한다`() {
        val funding = funding()
        givenFunding(funding)
        givenSummary(confirmedAmount = 50_000L, participantCount = 3L, pendingCount = 2L)

        val exception = assertFailsWith<BusinessException> {
            service.prepare(OWNER_ID, FUNDING_ID, excludePendingContributions = false)
        }
        assertEquals(FundingErrorCode.PENDING_CONTRIBUTION_EXISTS, exception.errorCode)
        assertNull(funding.giftPreparedAt)

        val response = service.prepare(OWNER_ID, FUNDING_ID, excludePendingContributions = true)

        assertEquals(FundingStatus.WAITING_REVEAL, funding.status)
        assertEquals(50_000L, response.confirmedAmount)
        assertEquals(2L, response.pendingContributionCount)
        assertFalse(response.pendingCleanupRequired)
    }

    @Test
    fun `이미 선물 준비를 완료한 서프라이즈 재요청은 상태와 참여를 다시 바꾸지 않는다`() {
        val preparedAt = NOW.minusHours(2)
        val funding = funding(status = FundingStatus.WAITING_REVEAL).also {
            setField(it, "giftPreparedAt", preparedAt)
        }
        givenFunding(funding)
        givenSummary(confirmedAmount = 80_000L, participantCount = 4L, pendingCount = 1L)

        val response = service.prepare(OWNER_ID, FUNDING_ID, excludePendingContributions = false)

        assertEquals(FundingStatus.WAITING_REVEAL, funding.status)
        assertEquals(preparedAt, response.giftPreparedAt)
        assertFalse(response.pendingCleanupRequired)
        verify(contributionRepository, never()).convertAwaitingTransfersToMessageOnly(FUNDING_ID, NOW)
    }

    @Test
    fun `준비 시각 없이 공개 대기인 기존 서프라이즈 재요청은 오류 없이 기존 결과를 반환한다`() {
        val funding = funding(status = FundingStatus.WAITING_REVEAL)
        givenFunding(funding)
        givenSummary(confirmedAmount = 80_000L, participantCount = 4L, pendingCount = 1L)

        val response = service.prepare(OWNER_ID, FUNDING_ID, excludePendingContributions = false)

        assertEquals(FundingStatus.WAITING_REVEAL, funding.status)
        assertNull(response.giftPreparedAt)
        assertFalse(response.pendingCleanupRequired)
        verify(contributionRepository, never()).convertAwaitingTransfersToMessageOnly(FUNDING_ID, NOW)
    }

    @Test
    fun `주최자가 아니거나 마감 전이거나 생일 펀딩이면 선물 준비에 접근할 수 없다`() {
        givenFunding(funding())
        val notOwner = assertFailsWith<BusinessException> { service.getPreparation(OWNER_ID + 1, FUNDING_ID) }
        assertEquals(FundingErrorCode.FUNDING_OWNER_ONLY, notOwner.errorCode)

        givenFunding(funding(deadline = NOW.plusSeconds(1)))
        val notEnded = assertFailsWith<BusinessException> { service.getPreparation(OWNER_ID, FUNDING_ID) }
        assertEquals(FundingErrorCode.FUNDING_NOT_ENDED, notEnded.errorCode)

        givenFunding(funding(type = FundingType.BIRTHDAY))
        val notSurprise = assertFailsWith<BusinessException> { service.getPreparation(OWNER_ID, FUNDING_ID) }
        assertEquals(FundingErrorCode.SURPRISE_FUNDING_REQUIRED, notSurprise.errorCode)

        verifyNoInteractions(contributionRepository)
    }

    @Test
    fun `취소된 서프라이즈는 찾을 수 없다`() {
        givenFunding(funding(status = FundingStatus.CANCELLED))

        val exception = assertFailsWith<BusinessException> { service.getPreparation(OWNER_ID, FUNDING_ID) }

        assertEquals(FundingErrorCode.FUNDING_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `받는 사람이 탈퇴해도 선물 준비 화면을 조회하고 탈퇴 여부를 알려준다`() {
        givenFunding(funding(status = FundingStatus.FUNDING))
        givenSummary(confirmedAmount = 80_000L, participantCount = 4L)
        given(userRepository.findById(RECIPIENT_ID)).willReturn(
            Optional.of(
                User(kakaoId = 1L, nickname = "지유 닉네임", profileImageUrl = null, name = "지유")
                    .also { it.withdraw(NOW) },
            ),
        )

        val response = service.getPreparation(OWNER_ID, FUNDING_ID)

        assertTrue(response.recipientWithdrawn)
        assertEquals("탈퇴한 사용자", response.recipientName)
    }

    private fun givenFunding(funding: Funding) {
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)
        given(userRepository.findById(RECIPIENT_ID)).willReturn(
            Optional.of(User(kakaoId = 1L, nickname = "지유 닉네임", profileImageUrl = null, name = "지유")),
        )
    }

    private fun givenSummary(confirmedAmount: Long, participantCount: Long, pendingCount: Long = 0L) {
        given(contributionRepository.summarizeByFundingIds(listOf(FUNDING_ID))).willReturn(
            listOf(
                FundingContributionSummary(
                    fundingId = FUNDING_ID,
                    confirmedAmount = confirmedAmount,
                    pendingAmount = pendingCount * 10_000L,
                    pendingCount = pendingCount,
                    participantCount = participantCount,
                ),
            ),
        )
    }

    private fun funding(
        type: FundingType = FundingType.SURPRISE,
        deadline: LocalDateTime = NOW.minusHours(1),
        status: FundingStatus = FundingStatus.CLOSED,
    ): Funding =
        Funding(
            ownerId = OWNER_ID,
            recipientId = if (type == FundingType.SURPRISE) RECIPIENT_ID else null,
            type = type,
            title = null,
            targetAmount = 100_000L,
            deadline = deadline,
            revealAt = if (type == FundingType.SURPRISE) deadline.plusDays(1) else null,
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
                accountHolder = "민지",
            ),
        ).also { funding ->
            setField(funding, "id", FUNDING_ID)
            setField(funding, "status", status)
            setField(funding, "createdAt", NOW.minusDays(10))
        }

    private fun setField(target: Any, fieldName: String, value: Any) {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            runCatching { type.getDeclaredField(fieldName) }.getOrNull()?.let {
                it.isAccessible = true
                it.set(target, value)
                return
            }
            type = type.superclass
        }
        error("$fieldName 필드를 찾을 수 없습니다")
    }

    companion object {
        private const val OWNER_ID = 1L
        private const val RECIPIENT_ID = 2L
        private const val FUNDING_ID = 12L
        private val NOW = LocalDateTime.of(2026, 11, 15, 0, 0)
    }
}
