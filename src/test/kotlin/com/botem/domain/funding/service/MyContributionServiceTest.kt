package com.botem.domain.funding.service

import com.botem.domain.account.entity.Bank
import com.botem.domain.account.service.AccountNumberCipher
import com.botem.domain.funding.dto.request.UpdateMyContributionRequest
import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.entity.RollingPaperMessage
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.RollingPaperMessageRepository
import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import com.botem.global.property.PayoutAccountEncryptionProperties
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
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

class MyContributionServiceTest {

    private val fundingRepository = mock(FundingRepository::class.java)
    private val contributionRepository = mock(ContributionRepository::class.java)
    private val rollingPaperMessageRepository = mock(RollingPaperMessageRepository::class.java)
    private val cipher = AccountNumberCipher(
        PayoutAccountEncryptionProperties(key = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="),
    )
    private val service = MyContributionService(
        fundingRepository = fundingRepository,
        contributionRepository = contributionRepository,
        rollingPaperMessageRepository = rollingPaperMessageRepository,
        accountNumberCipher = cipher,
        clock = Clock.fixed(Instant.parse("2026-10-21T00:41:00Z"), ZoneId.of("Asia/Seoul")),
    )

    @Test
    fun `송금이 필요한 보탬에만 계좌를 보여주고 마감 전이면 수정 가능하다`() {
        val funding = birthday(deadline = NOW.plusDays(3))
        val awaiting = contribution(1L, ContributionStatus.AWAITING_TRANSFER)
        val rejected = contribution(2L, ContributionStatus.REJECTED)
        val confirmed = contribution(3L, ContributionStatus.PENDING_CONFIRMATION).also { it.confirm(NOW) }
        givenContributions(funding, awaiting, rejected, confirmed)
        given(rollingPaperMessageRepository.findAllByContributionIdIn(listOf(1L, 2L, 3L)))
            .willReturn(listOf(message(2L, "생일 축하해!")))

        val response = service.getMyContributions(USER_ID, FUNDING_ID)

        assertTrue(response.editable)
        assertEquals("100159014821", response.contributions[0].payoutAccount?.accountNumber)
        assertEquals(Bank.TOSS_BANK, response.contributions[1].payoutAccount?.bank)
        assertNull(response.contributions[2].payoutAccount)
        assertEquals("생일 축하해!", response.contributions[1].message)
        assertNull(response.contributions[0].message)
        assertFalse(response.contributions.any { it.excludedFromResult })
    }

    @Test
    fun `팡 이후 남은 확인 대기 보탬은 결과에서 빠졌다고 표시하고 수정할 수 없다`() {
        val funding = birthday(deadline = NOW.minusDays(1)).also { it.openResult(NOW) }
        givenContributions(funding, contribution(1L, ContributionStatus.PENDING_CONFIRMATION))

        val response = service.getMyContributions(USER_ID, FUNDING_ID)

        assertFalse(response.editable)
        assertTrue(response.contributions.single().excludedFromResult)
    }

    @Test
    fun `선물 준비를 마친 서프라이즈의 확인 대기 보탬도 결과에서 빠졌다고 표시한다`() {
        val funding = surprise().also { setField(it, "status", FundingStatus.WAITING_REVEAL) }
        givenContributions(funding, contribution(1L, ContributionStatus.PENDING_CONFIRMATION))

        assertTrue(service.getMyContributions(USER_ID, FUNDING_ID).contributions.single().excludedFromResult)
    }

    @Test
    fun `공개 전 서프라이즈를 받는 사람은 내 보탬을 조회할 수 없다`() {
        given(fundingRepository.findById(FUNDING_ID)).willReturn(Optional.of(surprise()))

        val exception = assertFailsWith<BusinessException> { service.getMyContributions(RECIPIENT_ID, FUNDING_ID) }

        assertEquals(FundingErrorCode.SURPRISE_NOT_REVEALED, exception.errorCode)
    }

    @Test
    fun `공개 전 서프라이즈를 받는 사람은 보탬 존재와 상관없이 같은 오류로 수정이 막힌다`() {
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(surprise())

        val exception = assertFailsWith<BusinessException> {
            service.updateMyContribution(RECIPIENT_ID, FUNDING_ID, 1L, request())
        }

        assertEquals(FundingErrorCode.SURPRISE_NOT_REVEALED, exception.errorCode)
        verifyNoInteractions(contributionRepository)
    }

    @Test
    fun `취소된 펀딩의 내 보탬은 조회할 수 없다`() {
        val cancelled = birthday(deadline = NOW.plusDays(3)).also { setField(it, "status", FundingStatus.CANCELLED) }
        given(fundingRepository.findById(FUNDING_ID)).willReturn(Optional.of(cancelled))

        val exception = assertFailsWith<BusinessException> { service.getMyContributions(USER_ID, FUNDING_ID) }

        assertEquals(FundingErrorCode.FUNDING_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `마감일 전에는 이름과 메시지와 편지지를 수정한다`() {
        val contribution = contribution(1L, ContributionStatus.PENDING_CONFIRMATION)
        val message = message(1L, "생일 축하해!")
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(birthday(deadline = NOW.plusDays(3)))
        given(contributionRepository.findByIdAndFundingIdForUpdate(1L, FUNDING_ID)).willReturn(contribution)
        given(rollingPaperMessageRepository.findByContributionId(1L)).willReturn(message)

        val response = service.updateMyContribution(USER_ID, FUNDING_ID, 1L, request(name = "  하린 "))

        assertEquals("하린", response.participantName)
        assertEquals("진짜 축하해!", message.content)
        assertEquals("BLUE_DOT_GRID", message.paperTemplateCode)
        assertNull(response.payoutAccount)
    }

    @Test
    fun `메시지가 없던 보탬을 수정하면 메시지를 새로 저장한다`() {
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(birthday(deadline = NOW.plusDays(3)))
        given(contributionRepository.findByIdAndFundingIdForUpdate(1L, FUNDING_ID))
            .willReturn(contribution(1L, ContributionStatus.AWAITING_TRANSFER))
        given(rollingPaperMessageRepository.findByContributionId(1L)).willReturn(null)
        given(rollingPaperMessageRepository.save(any(RollingPaperMessage::class.java))).willAnswer { it.arguments[0] }

        val response = service.updateMyContribution(USER_ID, FUNDING_ID, 1L, request())

        assertEquals("진짜 축하해!", response.message)
        verify(rollingPaperMessageRepository).save(any(RollingPaperMessage::class.java))
    }

    @Test
    fun `다른 사람의 보탬이나 취소된 보탬이나 마감일이 지난 보탬은 수정할 수 없다`() {
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(birthday(deadline = NOW.plusDays(3)))
        given(contributionRepository.findByIdAndFundingIdForUpdate(1L, FUNDING_ID))
            .willReturn(contribution(1L, ContributionStatus.AWAITING_TRANSFER, participantUserId = OTHER_USER_ID))
        given(contributionRepository.findByIdAndFundingIdForUpdate(2L, FUNDING_ID))
            .willReturn(contribution(2L, ContributionStatus.AWAITING_TRANSFER).also(Contribution::cancel))

        val notMine = assertFailsWith<BusinessException> { service.updateMyContribution(USER_ID, FUNDING_ID, 1L, request()) }
        val cancelled = assertFailsWith<BusinessException> { service.updateMyContribution(USER_ID, FUNDING_ID, 2L, request()) }

        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(birthday(deadline = NOW))
        given(contributionRepository.findByIdAndFundingIdForUpdate(3L, FUNDING_ID))
            .willReturn(contribution(3L, ContributionStatus.AWAITING_TRANSFER))
        val closed = assertFailsWith<BusinessException> { service.updateMyContribution(USER_ID, FUNDING_ID, 3L, request()) }

        assertEquals(FundingErrorCode.CONTRIBUTION_PARTICIPANT_ONLY, notMine.errorCode)
        assertEquals(FundingErrorCode.CONTRIBUTION_NOT_FOUND, cancelled.errorCode)
        assertEquals(FundingErrorCode.CONTRIBUTION_NOT_EDITABLE, closed.errorCode)
    }

    @Test
    fun `조기 마감한 서프라이즈는 마감일 전이어도 수정할 수 없고 생일 펀딩은 수정할 수 있다`() {
        val earlyClosedSurprise = funding(FundingType.SURPRISE, RECIPIENT_ID, NOW.plusDays(3), NOW.plusDays(4))
            .also { it.closeEarly(NOW) }
        val earlyClosedBirthday = birthday(deadline = NOW.plusDays(3)).also { it.closeEarly(NOW) }
        given(contributionRepository.findByIdAndFundingIdForUpdate(1L, FUNDING_ID))
            .willReturn(contribution(1L, ContributionStatus.MESSAGE_ONLY))
        given(rollingPaperMessageRepository.findByContributionId(1L)).willReturn(message(1L, "생일 축하해!"))

        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(earlyClosedSurprise)
        val exception = assertFailsWith<BusinessException> {
            service.updateMyContribution(USER_ID, FUNDING_ID, 1L, request())
        }
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(earlyClosedBirthday)
        val updated = service.updateMyContribution(USER_ID, FUNDING_ID, 1L, request())

        assertEquals(FundingErrorCode.CONTRIBUTION_NOT_EDITABLE, exception.errorCode)
        assertEquals("진짜 축하해!", updated.message)
    }

    @Test
    fun `보탠 적이 없으면 빈 목록을 반환해 보태기 버튼을 보여주게 한다`() {
        givenContributions(birthday(deadline = NOW.plusDays(3)))

        val response = service.getMyContributions(USER_ID, FUNDING_ID)

        assertTrue(response.editable)
        assertEquals(emptyList(), response.contributions)
    }

    @Test
    fun `없는 펀딩이거나 공백 문자만 있는 메시지면 수정하지 않는다`() {
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(null)
        val notFound = assertFailsWith<BusinessException> { service.updateMyContribution(USER_ID, FUNDING_ID, 1L, request()) }

        // NBSP는 @NotBlank를 통과하므로 서비스에서 한 번 더 걸러야 한다
        val blank = assertFailsWith<BusinessException> {
            service.updateMyContribution(
                USER_ID,
                FUNDING_ID,
                1L,
                UpdateMyContributionRequest("민지", "\u00A0", "BLUE_DOT_GRID"),
            )
        }

        assertEquals(FundingErrorCode.FUNDING_NOT_FOUND, notFound.errorCode)
        assertEquals(CommonErrorCode.INVALID_INPUT_VALUE, blank.errorCode)
    }

    private fun givenContributions(funding: Funding, vararg contributions: Contribution) {
        given(fundingRepository.findById(FUNDING_ID)).willReturn(Optional.of(funding))
        given(
            contributionRepository.findAllByFundingIdAndParticipantUserIdAndStatusNotOrderByCreatedAtAscIdAsc(
                FUNDING_ID,
                USER_ID,
                ContributionStatus.CANCELLED,
            ),
        ).willReturn(contributions.toList())
    }

    private fun request(name: String = "민지"): UpdateMyContributionRequest =
        UpdateMyContributionRequest(participantName = name, message = " 진짜 축하해! ", paperTemplateCode = "BLUE_DOT_GRID")

    private fun contribution(
        id: Long,
        status: ContributionStatus,
        participantUserId: Long = USER_ID,
    ): Contribution =
        Contribution(
            fundingId = FUNDING_ID,
            participantUserId = participantUserId,
            participantName = "민지",
            amount = 30_000L,
            status = status,
            guestPasswordHash = null,
        ).also {
            setField(it, "id", id)
            setField(it, "createdAt", NOW.minusDays(1))
        }

    private fun message(contributionId: Long, content: String): RollingPaperMessage =
        RollingPaperMessage(contributionId = contributionId, content = content, paperTemplateCode = "LIME_DOT_GRID")

    private fun birthday(deadline: LocalDateTime): Funding =
        funding(FundingType.BIRTHDAY, null, deadline, null)

    private fun surprise(): Funding =
        funding(FundingType.SURPRISE, RECIPIENT_ID, NOW.minusDays(1), NOW.minusHours(1))

    private fun funding(
        type: FundingType,
        recipientId: Long?,
        deadline: LocalDateTime,
        revealAt: LocalDateTime?,
    ): Funding =
        Funding(
            ownerId = 99L,
            recipientId = recipientId,
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
                bank = Bank.TOSS_BANK,
                encryptedAccountNumber = cipher.encrypt("100159014821"),
                accountHolder = null,
            ),
        ).also { setField(it, "id", FUNDING_ID) }

    // createdAt은 상위 클래스(BaseEntity)에 있어 계층을 따라 올라가며 찾는다
    private fun setField(target: Any, fieldName: String, value: Any) {
        val field = generateSequence<Class<*>>(target.javaClass) { it.superclass }
            .firstNotNullOf { type -> type.declaredFields.firstOrNull { it.name == fieldName } }
        field.isAccessible = true
        field.set(target, value)
    }

    companion object {
        private const val USER_ID = 1L
        private const val OTHER_USER_ID = 2L
        private const val RECIPIENT_ID = 3L
        private const val FUNDING_ID = 10L
        private val NOW = LocalDateTime.of(2026, 10, 21, 9, 41)
    }
}
