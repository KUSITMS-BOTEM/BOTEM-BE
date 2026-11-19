package com.botem.domain.funding.service

import com.botem.domain.account.entity.Bank
import com.botem.domain.funding.dto.request.ContributionDecision
import com.botem.domain.funding.dto.request.DecideContributionsRequest
import com.botem.domain.funding.dto.request.UndoContributionDecisionsRequest
import com.botem.domain.funding.entity.Contribution
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
import com.botem.domain.user.repository.UserRepository
import com.botem.global.entity.BaseEntity
import com.botem.global.exception.BusinessException
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyIterable
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DepositConfirmationServiceTest {

    private val fundingRepository = mock(FundingRepository::class.java)
    private val contributionRepository = mock(ContributionRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val service = DepositConfirmationService(
        fundingRepository = fundingRepository,
        contributionRepository = contributionRepository,
        userRepository = userRepository,
        fundingContributionCloser = FundingContributionCloser(contributionRepository),
        clock = Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), ZoneId.of("Asia/Seoul")),
    )

    @Test
    fun `확인 대기 목록은 이름과 참여 시각과 금액을 반환한다`() {
        given(fundingRepository.findById(FUNDING_ID)).willReturn(Optional.of(funding()))
        given(
            contributionRepository.findAllByFundingIdAndStatusOrderByCreatedAtAscIdAsc(
                FUNDING_ID,
                ContributionStatus.PENDING_CONFIRMATION,
            ),
        ).willReturn(listOf(contribution(id = 31L, status = ContributionStatus.PENDING_CONFIRMATION)))
        given(userRepository.findAllById(anyIterable())).willReturn(emptyList())

        val response = service.getPendingContributions(OWNER_ID, FUNDING_ID)

        assertEquals(1, response.pendingContributionCount)
        assertEquals(31L, response.contributions.single().contributionId)
        assertEquals("하린", response.contributions.single().participantName)
        assertEquals(NOW.minusDays(1), response.contributions.single().participatedAt)
        assertEquals(10_000L, response.contributions.single().amount)
    }

    @Test
    fun `확인 대기 참여를 확정하고 확인 시각과 처리 후 진행률을 반환한다`() {
        givenLockedFunding(funding())
        val contribution = givenContribution(ContributionStatus.PENDING_CONFIRMATION)
        givenSummary(confirmedAmount = 55_000L, pendingAmount = 15_000L, pendingCount = 1L)

        val response = service.confirm(OWNER_ID, FUNDING_ID, CONTRIBUTION_ID)

        assertEquals(ContributionStatus.CONFIRMED, contribution.status)
        assertEquals(NOW, contribution.confirmedAt)
        assertEquals(ContributionStatus.CONFIRMED, response.status)
        assertEquals(55, response.progress.confirmedPercentage)
        assertEquals(15, response.progress.pendingPercentage)
        assertEquals(1L, response.progress.pendingContributionCount)
    }

    @Test
    fun `이미 확정된 참여를 다시 확정하면 확인 시각을 바꾸지 않는다`() {
        givenLockedFunding(funding())
        val contribution = givenContribution(ContributionStatus.PENDING_CONFIRMATION)
        contribution.confirm(NOW.minusHours(1))
        givenSummary(confirmedAmount = 10_000L)

        val response = service.confirm(OWNER_ID, FUNDING_ID, CONTRIBUTION_ID)

        assertEquals(ContributionStatus.CONFIRMED, response.status)
        assertEquals(NOW.minusHours(1), contribution.confirmedAt)
    }

    @Test
    fun `확인 대기가 아닌 참여는 확정할 수 없다`() {
        givenLockedFunding(funding())

        listOf(ContributionStatus.AWAITING_TRANSFER, ContributionStatus.MESSAGE_ONLY).forEach { status ->
            givenContribution(status)
            assertErrorCode(FundingErrorCode.CONTRIBUTION_NOT_PENDING) {
                service.confirm(OWNER_ID, FUNDING_ID, CONTRIBUTION_ID)
            }
        }

        givenContribution(ContributionStatus.PENDING_CONFIRMATION).reject(NOW)
        assertErrorCode(FundingErrorCode.CONTRIBUTION_NOT_PENDING) {
            service.confirm(OWNER_ID, FUNDING_ID, CONTRIBUTION_ID)
        }
    }

    @Test
    fun `확인 대기 참여를 미입금 처리하고 다시 요청해도 그대로 유지한다`() {
        givenLockedFunding(funding())
        val contribution = givenContribution(ContributionStatus.PENDING_CONFIRMATION)
        givenSummary()

        service.reject(OWNER_ID, FUNDING_ID, CONTRIBUTION_ID)
        val response = service.reject(OWNER_ID, FUNDING_ID, CONTRIBUTION_ID)

        assertEquals(ContributionStatus.REJECTED, contribution.status)
        assertEquals(NOW, contribution.rejectedAt)
        assertEquals(ContributionStatus.REJECTED, response.status)
    }

    @Test
    fun `확정된 참여는 미입금 처리할 수 없다`() {
        givenLockedFunding(funding())
        givenContribution(ContributionStatus.PENDING_CONFIRMATION).confirm(NOW)

        assertErrorCode(FundingErrorCode.CONTRIBUTION_NOT_PENDING) {
            service.reject(OWNER_ID, FUNDING_ID, CONTRIBUTION_ID)
        }
    }

    @Test
    fun `확인 대기를 모두 확정하고 목표를 넘어도 허용한다`() {
        givenLockedFunding(funding())
        val pending = listOf(
            contribution(id = 31L, status = ContributionStatus.PENDING_CONFIRMATION),
            contribution(id = 32L, status = ContributionStatus.PENDING_CONFIRMATION),
        )
        given(
            contributionRepository.findAllByFundingIdAndStatusForUpdate(
                FUNDING_ID,
                ContributionStatus.PENDING_CONFIRMATION,
            ),
        ).willReturn(pending)
        givenSummary(confirmedAmount = 120_000L)

        val response = service.confirmAll(OWNER_ID, FUNDING_ID)

        assertEquals(2, response.confirmedCount)
        assertTrue(pending.all { it.status == ContributionStatus.CONFIRMED })
        assertEquals(120, response.progress.confirmedPercentage)
        assertTrue(response.progress.targetReached)
    }

    @Test
    fun `확정 금액이 목표에 닿으면 보태기를 마감하고 송금 전 참여를 메시지 전용으로 바꾼다`() {
        val funding = funding()
        givenLockedFunding(funding)
        givenContribution(ContributionStatus.PENDING_CONFIRMATION)
        givenSummary(confirmedAmount = 100_000L)

        service.confirm(OWNER_ID, FUNDING_ID, CONTRIBUTION_ID)

        assertEquals(FundingStatus.CLOSED, funding.status)
        verify(contributionRepository).convertAwaitingTransfersToMessageOnly(FUNDING_ID, NOW)
    }

    @Test
    fun `확정 금액이 목표에 못 미치면 보태기를 마감하지 않는다`() {
        val funding = funding()
        givenLockedFunding(funding)
        givenContribution(ContributionStatus.PENDING_CONFIRMATION)
        givenSummary(confirmedAmount = 99_999L)

        service.confirm(OWNER_ID, FUNDING_ID, CONTRIBUTION_ID)

        assertEquals(FundingStatus.FUNDING, funding.status)
        verify(contributionRepository, never()).convertAwaitingTransfersToMessageOnly(FUNDING_ID, NOW)
    }

    @Test
    fun `결과를 연 펀딩은 입금 확인 상태를 바꿀 수 없다`() {
        givenLockedFunding(funding().also { it.openResult(NOW.plusDays(5)) })

        assertErrorCode(FundingErrorCode.FUNDING_RESULT_ALREADY_OPENED) {
            service.confirm(OWNER_ID, FUNDING_ID, CONTRIBUTION_ID)
        }
        assertErrorCode(FundingErrorCode.FUNDING_RESULT_ALREADY_OPENED) {
            service.confirmAll(OWNER_ID, FUNDING_ID)
        }
    }

    @Test
    fun `선물 준비를 완료한 서프라이즈는 입금 확인 상태를 바꿀 수 없다`() {
        givenLockedFunding(funding(status = FundingStatus.WAITING_REVEAL, type = FundingType.SURPRISE))

        assertErrorCode(FundingErrorCode.FUNDING_RESULT_ALREADY_OPENED) {
            service.reject(OWNER_ID, FUNDING_ID, CONTRIBUTION_ID)
        }
        assertErrorCode(FundingErrorCode.FUNDING_RESULT_ALREADY_OPENED) {
            service.confirmAll(OWNER_ID, FUNDING_ID)
        }
    }

    @Test
    fun `개설자가 아니거나 취소된 펀딩이거나 다른 펀딩의 참여면 처리할 수 없다`() {
        givenLockedFunding(funding())
        assertErrorCode(FundingErrorCode.FUNDING_OWNER_ONLY) {
            service.confirm(OTHER_USER_ID, FUNDING_ID, CONTRIBUTION_ID)
        }
        assertErrorCode(FundingErrorCode.CONTRIBUTION_NOT_FOUND) {
            service.reject(OWNER_ID, FUNDING_ID, CONTRIBUTION_ID)
        }

        givenLockedFunding(funding(status = FundingStatus.CANCELLED))
        assertErrorCode(FundingErrorCode.FUNDING_NOT_FOUND) {
            service.confirm(OWNER_ID, FUNDING_ID, CONTRIBUTION_ID)
        }
    }

    @Test
    fun `선택한 확인 대기 참여를 한 번에 받았어요로 처리한다`() {
        givenLockedFunding(funding())
        val contributions = givenContributions(31L to ContributionStatus.PENDING_CONFIRMATION, 32L to ContributionStatus.PENDING_CONFIRMATION)
        contributions[1].confirm(NOW.minusMinutes(5))
        givenSummary(confirmedAmount = 20_000L)

        val response = service.decide(OWNER_ID, FUNDING_ID, DecideContributionsRequest(listOf(31L, 32L, 31L), ContributionDecision.CONFIRM))

        assertTrue(contributions.all { it.status == ContributionStatus.CONFIRMED })
        assertEquals(NOW, contributions[0].confirmedAt)
        assertEquals(NOW.minusMinutes(5), contributions[1].confirmedAt)
        // 이미 확정돼 있던 32는 이번 요청으로 바뀌지 않았으므로 실행 취소 대상이 아니다
        assertEquals(listOf(31L to true, 32L to false), response.contributions.map { it.contributionId to it.changed })
        assertEquals(20, response.progress.confirmedPercentage)
    }

    @Test
    fun `선택한 확인 대기 참여를 한 번에 못 받았어요로 처리한다`() {
        givenLockedFunding(funding())
        val contributions = givenContributions(31L to ContributionStatus.PENDING_CONFIRMATION, 32L to ContributionStatus.PENDING_CONFIRMATION)
        givenSummary()

        val response = service.decide(OWNER_ID, FUNDING_ID, DecideContributionsRequest(listOf(31L, 32L), ContributionDecision.REJECT))

        assertTrue(contributions.all { it.status == ContributionStatus.REJECTED && it.rejectedAt == NOW })
        assertTrue(response.contributions.all { it.status == ContributionStatus.REJECTED })
    }

    @Test
    fun `선택한 참여 중 이 펀딩에 없는 참여가 있으면 처리하지 않는다`() {
        givenLockedFunding(funding())
        val found = contribution(id = 31L, status = ContributionStatus.PENDING_CONFIRMATION)
        // 요청한 두 건 중 이 펀딩에 있는 한 건만 조회되는 상황
        given(contributionRepository.findAllByIdInAndFundingIdForUpdate(setOf(31L, 99L), FUNDING_ID))
            .willReturn(listOf(found))

        assertErrorCode(FundingErrorCode.CONTRIBUTION_NOT_FOUND) {
            service.decide(OWNER_ID, FUNDING_ID, DecideContributionsRequest(listOf(31L, 99L), ContributionDecision.CONFIRM))
        }
        assertEquals(ContributionStatus.PENDING_CONFIRMATION, found.status)
    }

    @Test
    fun `처리 직후에는 받았어요와 못 받았어요를 확인 대기로 되돌린다`() {
        givenLockedFunding(funding())
        val (confirmed, rejected, pending) = givenContributions(
            31L to ContributionStatus.PENDING_CONFIRMATION,
            32L to ContributionStatus.PENDING_CONFIRMATION,
            33L to ContributionStatus.PENDING_CONFIRMATION,
        )
        confirmed.confirm(NOW.minusSeconds(10))
        rejected.reject(NOW.minusSeconds(3))
        givenSummary(pendingAmount = 30_000L, pendingCount = 3L)

        val response = service.undo(OWNER_ID, FUNDING_ID, UndoContributionDecisionsRequest(listOf(31L, 32L, 33L)))

        listOf(confirmed, rejected, pending).forEach {
            assertEquals(ContributionStatus.PENDING_CONFIRMATION, it.status)
            assertEquals(null, it.confirmedAt)
            assertEquals(null, it.rejectedAt)
        }
        assertEquals(3L, response.progress.pendingContributionCount)
        assertEquals(listOf(true, true, false), response.contributions.map { it.changed })
    }

    @Test
    fun `실행 취소 시간이 지났거나 처리하지 않은 참여는 되돌리지 않는다`() {
        givenLockedFunding(funding())
        val (expired, fresh) = givenContributions(
            31L to ContributionStatus.PENDING_CONFIRMATION,
            32L to ContributionStatus.PENDING_CONFIRMATION,
        )
        expired.confirm(NOW.minusSeconds(11))
        fresh.reject(NOW)

        assertErrorCode(FundingErrorCode.CONTRIBUTION_DECISION_UNDO_EXPIRED) {
            service.undo(OWNER_ID, FUNDING_ID, UndoContributionDecisionsRequest(listOf(31L, 32L)))
        }
        assertEquals(ContributionStatus.CONFIRMED, expired.status)

        givenContributions(41L to ContributionStatus.AWAITING_TRANSFER)
        assertErrorCode(FundingErrorCode.CONTRIBUTION_DECISION_NOT_UNDOABLE) {
            service.undo(OWNER_ID, FUNDING_ID, UndoContributionDecisionsRequest(listOf(41L)))
        }
    }

    @Test
    fun `처리 시각이 없는 기존 못 받았어요는 되돌릴 수 없다`() {
        givenLockedFunding(funding())
        val legacy = givenContributions(31L to ContributionStatus.PENDING_CONFIRMATION).single()
        legacy.reject(NOW)
        setField(legacy, Contribution::class.java, "rejectedAt", null)

        assertErrorCode(FundingErrorCode.CONTRIBUTION_DECISION_UNDO_EXPIRED) {
            service.undo(OWNER_ID, FUNDING_ID, UndoContributionDecisionsRequest(listOf(31L)))
        }
    }

    @Test
    fun `결과를 연 펀딩은 선택 처리와 실행 취소를 할 수 없다`() {
        givenLockedFunding(funding().also { it.openResult(NOW.plusDays(5)) })

        assertErrorCode(FundingErrorCode.FUNDING_RESULT_ALREADY_OPENED) {
            service.decide(OWNER_ID, FUNDING_ID, DecideContributionsRequest(listOf(31L), ContributionDecision.REJECT))
        }
        assertErrorCode(FundingErrorCode.FUNDING_RESULT_ALREADY_OPENED) {
            service.undo(OWNER_ID, FUNDING_ID, UndoContributionDecisionsRequest(listOf(31L)))
        }
    }

    private fun givenContributions(vararg idAndStatuses: Pair<Long, ContributionStatus>): List<Contribution> {
        val contributions = idAndStatuses.map { (id, status) -> contribution(id = id, status = status) }
        given(contributionRepository.findAllByIdInAndFundingIdForUpdate(contributions.map { it.id }.toSet(), FUNDING_ID))
            .willReturn(contributions)
        return contributions
    }

    private fun givenLockedFunding(funding: Funding) {
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)
    }

    private fun givenContribution(status: ContributionStatus): Contribution =
        contribution(id = CONTRIBUTION_ID, status = status).also {
            given(contributionRepository.findByIdAndFundingIdForUpdate(CONTRIBUTION_ID, FUNDING_ID)).willReturn(it)
        }

    private fun givenSummary(confirmedAmount: Long = 0L, pendingAmount: Long = 0L, pendingCount: Long = 0L) {
        given(contributionRepository.summarizeByFundingIds(listOf(FUNDING_ID))).willReturn(
            listOf(FundingContributionSummary(FUNDING_ID, confirmedAmount, pendingAmount, pendingCount, 1L)),
        )
    }

    // 생성자는 확정 상태를 받지 않으므로 확인 대기는 송금 완료 표시를 거쳐 만든다
    private fun contribution(id: Long, status: ContributionStatus): Contribution {
        val initialStatus = if (status == ContributionStatus.PENDING_CONFIRMATION) {
            ContributionStatus.AWAITING_TRANSFER
        } else {
            status
        }

        return Contribution(
            fundingId = FUNDING_ID,
            participantUserId = null,
            participantName = "하린",
            amount = if (status == ContributionStatus.MESSAGE_ONLY) 0L else 10_000L,
            status = initialStatus,
            guestPasswordHash = null,
        ).also { contribution ->
            if (status == ContributionStatus.PENDING_CONFIRMATION) {
                contribution.markTransferSent()
            }
            setField(contribution, Contribution::class.java, "id", id)
            setField(contribution, BaseEntity::class.java, "createdAt", NOW.minusDays(1))
        }
    }

    private fun funding(
        status: FundingStatus = FundingStatus.FUNDING,
        type: FundingType = FundingType.BIRTHDAY,
    ): Funding =
        Funding(
            ownerId = OWNER_ID,
            recipientId = if (type == FundingType.SURPRISE) OTHER_USER_ID else null,
            type = type,
            title = "지유의 생일",
            targetAmount = 100_000L,
            deadline = NOW.plusDays(3),
            revealAt = if (type == FundingType.SURPRISE) NOW.plusDays(4) else null,
            story = "새 헤드폰이 필요해요",
            promise = null,
            product = FundingProductSnapshot(
                sourceWishId = 1L,
                name = "소니 WH-1000XM5",
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
            setField(funding, Funding::class.java, "id", FUNDING_ID)
            setField(funding, Funding::class.java, "status", status)
        }

    private fun assertErrorCode(errorCode: FundingErrorCode, block: () -> Unit) {
        assertEquals(errorCode, assertFailsWith<BusinessException>(block = block).errorCode)
    }

    private fun setField(target: Any, owner: Class<*>, fieldName: String, value: Any?) {
        owner.getDeclaredField(fieldName).apply {
            isAccessible = true
            set(target, value)
        }
    }

    companion object {
        private const val FUNDING_ID = 12L
        private const val CONTRIBUTION_ID = 31L
        private const val OWNER_ID = 1L
        private const val OTHER_USER_ID = 2L
        private val NOW = LocalDateTime.of(2026, 11, 18, 12, 0)
    }
}
