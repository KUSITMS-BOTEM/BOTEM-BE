package com.botem.domain.funding.service

import com.botem.domain.account.entity.Bank
import com.botem.domain.funding.dto.request.AccessGuestContributionRequest
import com.botem.domain.funding.dto.request.UpdateGuestContributionRequest
import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.entity.GuestContributionAccessAttempt
import com.botem.domain.funding.entity.RollingPaperMessage
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.GuestContributionAccessAttemptRepository
import com.botem.domain.funding.repository.RollingPaperMessageRepository
import com.botem.global.exception.BusinessException
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.util.ReflectionTestUtils.setField
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class GuestContributionServiceTest {

    private val fundingRepository = mock(FundingRepository::class.java)
    private val contributionRepository = mock(ContributionRepository::class.java)
    private val rollingPaperMessageRepository = mock(RollingPaperMessageRepository::class.java)
    private val accessAttemptRepository = mock(GuestContributionAccessAttemptRepository::class.java)
    private val accessTokenProvider = mock(GuestContributionAccessTokenProvider::class.java)
    private val service = GuestContributionService(
        fundingRepository = fundingRepository,
        contributionRepository = contributionRepository,
        rollingPaperMessageRepository = rollingPaperMessageRepository,
        accessAttemptRepository = accessAttemptRepository,
        accessTokenProvider = accessTokenProvider,
        clock = Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), ZoneId.of("Asia/Seoul")),
    )

    @Test
    fun `이름과 비밀번호가 일치하면 보탬을 조회하고 접근 토큰을 발급한다`() {
        val contribution = contribution()
        val message = message()
        givenFunding()
        given(accessAttemptRepository.findByFundingIdAndParticipantNameForUpdate(FUNDING_ID, "민지"))
            .willReturn(null)
        givenGuestContributions("민지", listOf(contribution))
        given(rollingPaperMessageRepository.findByContributionId(CONTRIBUTION_ID)).willReturn(message)
        given(accessTokenProvider.issue(FUNDING_ID, CONTRIBUTION_ID))
            .willReturn(IssuedGuestContributionAccessToken(ACCESS_TOKEN, TOKEN_EXPIRES_AT))

        val response = service.access(FUNDING_ID, AccessGuestContributionRequest("  민지  ", "1234"))

        assertEquals(CONTRIBUTION_ID, response.contributionId)
        assertEquals("민지", response.participantName)
        assertEquals("생일 축하해!", response.message)
        assertEquals(ACCESS_TOKEN, response.accessToken)
        assertEquals(TOKEN_EXPIRES_AT, response.accessTokenExpiresAt)
    }

    @Test
    fun `존재하지 않는 이름도 비밀번호 불일치와 같은 오류를 반환하고 실패를 기록한다`() {
        val attempt = GuestContributionAccessAttempt(FUNDING_ID, "없는 이름")
        givenFunding()
        given(accessAttemptRepository.findByFundingIdAndParticipantNameForUpdate(FUNDING_ID, "없는 이름"))
            .willReturn(attempt)
        givenGuestContributions("없는 이름", emptyList())

        assertError(FundingErrorCode.CONTRIBUTION_PASSWORD_MISMATCH) {
            service.access(FUNDING_ID, AccessGuestContributionRequest("없는 이름", "1234"))
        }

        assertEquals(1, attempt.failedCount)
    }

    @Test
    fun `비밀번호를 다섯 번 틀리면 10분 동안 잠근다`() {
        val attempt = GuestContributionAccessAttempt(FUNDING_ID, "민지")
        repeat(4) { attempt.recordFailure(NOW) }
        givenFunding()
        given(accessAttemptRepository.findByFundingIdAndParticipantNameForUpdate(FUNDING_ID, "민지"))
            .willReturn(attempt)
        givenGuestContributions("민지", emptyList())

        assertError(FundingErrorCode.CONTRIBUTION_PASSWORD_MISMATCH) {
            service.access(FUNDING_ID, AccessGuestContributionRequest("민지", "0000"))
        }
        assertEquals(NOW.plusMinutes(10), attempt.lockedUntil)

        assertError(FundingErrorCode.CONTRIBUTION_PASSWORD_LOCKED) {
            service.access(FUNDING_ID, AccessGuestContributionRequest("민지", "1234"))
        }
    }

    @Test
    fun `인증에 성공하면 기존 실패 횟수를 초기화한다`() {
        val attempt = GuestContributionAccessAttempt(FUNDING_ID, "민지").also { it.recordFailure(NOW) }
        val contribution = contribution()
        givenFunding()
        given(accessAttemptRepository.findByFundingIdAndParticipantNameForUpdate(FUNDING_ID, "민지"))
            .willReturn(attempt)
        givenGuestContributions("민지", listOf(contribution))
        given(rollingPaperMessageRepository.findByContributionId(CONTRIBUTION_ID)).willReturn(message())
        given(accessTokenProvider.issue(FUNDING_ID, CONTRIBUTION_ID))
            .willReturn(IssuedGuestContributionAccessToken(ACCESS_TOKEN, TOKEN_EXPIRES_AT))

        service.access(FUNDING_ID, AccessGuestContributionRequest("민지", "1234"))

        assertEquals(0, attempt.failedCount)
        assertNull(attempt.lockedUntil)
    }

    @Test
    fun `접근 토큰으로 참여자 이름과 롤링페이퍼를 수정한다`() {
        val contribution = contribution()
        val message = message()
        givenToken(contribution)
        givenGuestContributions("새 이름", emptyList())
        given(rollingPaperMessageRepository.findByContributionId(CONTRIBUTION_ID)).willReturn(message)

        val response = service.update(
            FUNDING_ID,
            ACCESS_TOKEN,
            UpdateGuestContributionRequest(" 새 이름 ", " 수정한 메시지 ", " BLACK_CONFETTI "),
        )

        assertEquals("새 이름", contribution.participantName)
        assertEquals("수정한 메시지", message.content)
        assertEquals("BLACK_CONFETTI", message.paperTemplateCode)
        assertEquals("새 이름", response.participantName)
    }

    @Test
    fun `다른 비회원 보탬이 사용 중인 이름으로 변경할 수 없다`() {
        val contribution = contribution()
        givenToken(contribution)
        givenGuestContributions("수빈", listOf(contribution(id = OTHER_CONTRIBUTION_ID, participantName = "수빈")))

        assertError(FundingErrorCode.CONTRIBUTION_GUEST_NAME_DUPLICATED) {
            service.update(
                FUNDING_ID,
                ACCESS_TOKEN,
                UpdateGuestContributionRequest("수빈", "메시지", "BLUE_DOT_GRID"),
            )
        }
        verify(rollingPaperMessageRepository, never()).findByContributionId(CONTRIBUTION_ID)
    }

    @Test
    fun `다른 펀딩에 발급된 접근 토큰은 사용할 수 없다`() {
        given(accessTokenProvider.parse(ACCESS_TOKEN))
            .willReturn(GuestContributionAccessPrincipal(OTHER_FUNDING_ID, CONTRIBUTION_ID))

        assertError(FundingErrorCode.CONTRIBUTION_GUEST_ACCESS_TOKEN_INVALID) {
            service.cancel(FUNDING_ID, ACCESS_TOKEN)
        }
        verify(contributionRepository, never()).findByIdAndFundingIdForUpdate(CONTRIBUTION_ID, FUNDING_ID)
    }

    @Test
    fun `송금 전 보탬은 삭제하고 입금 확인 중인 보탬은 삭제하지 않는다`() {
        val cancellable = contribution()
        givenToken(cancellable)

        service.cancel(FUNDING_ID, ACCESS_TOKEN)
        assertEquals(ContributionStatus.CANCELLED, cancellable.status)

        val pending = contribution(status = ContributionStatus.PENDING_CONFIRMATION)
        givenToken(pending)
        assertError(FundingErrorCode.CONTRIBUTION_NOT_CANCELLABLE) {
            service.cancel(FUNDING_ID, ACCESS_TOKEN)
        }
        assertEquals(ContributionStatus.PENDING_CONFIRMATION, pending.status)
    }

    private fun givenFunding() {
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding())
    }

    private fun givenGuestContributions(participantName: String, contributions: List<Contribution>) {
        given(
            contributionRepository.findAllByFundingIdAndParticipantUserIdIsNullAndParticipantNameAndStatusNot(
                FUNDING_ID,
                participantName,
                ContributionStatus.CANCELLED,
            ),
        ).willReturn(contributions)
    }

    private fun givenToken(contribution: Contribution) {
        given(accessTokenProvider.parse(ACCESS_TOKEN))
            .willReturn(GuestContributionAccessPrincipal(FUNDING_ID, CONTRIBUTION_ID))
        given(contributionRepository.findByIdAndFundingIdForUpdate(CONTRIBUTION_ID, FUNDING_ID))
            .willReturn(contribution)
    }

    private fun funding() = Funding(
        ownerId = 1L,
        recipientId = null,
        type = FundingType.BIRTHDAY,
        title = "생일 펀딩",
        targetAmount = 100_000L,
        deadline = NOW.plusDays(3),
        revealAt = null,
        story = "사연",
        promise = null,
        product = FundingProductSnapshot(null, "에어팟", "https://example.com/p.jpg", 300_000L, "https://example.com/p"),
        payoutAccount = FundingPayoutAccountSnapshot(null, Bank.TOSS_BANK, "encrypted", "김지유"),
    ).also { setField(it, "id", FUNDING_ID) }

    private fun contribution(
        id: Long = CONTRIBUTION_ID,
        participantName: String = "민지",
        status: ContributionStatus = ContributionStatus.AWAITING_TRANSFER,
    ) = Contribution(
        fundingId = FUNDING_ID,
        participantUserId = null,
        participantName = participantName,
        amount = 10_000L,
        status = status,
        guestPasswordHash = BCryptPasswordEncoder().encode("1234"),
        requestKey = "request-$id",
    ).also { setField(it, "id", id) }

    private fun message() = RollingPaperMessage(
        contributionId = CONTRIBUTION_ID,
        content = "생일 축하해!",
        paperTemplateCode = "BLUE_DOT_GRID",
    )

    private fun assertError(errorCode: FundingErrorCode, block: () -> Unit) {
        val exception = assertFailsWith<BusinessException>(block = block)
        assertEquals(errorCode, exception.errorCode)
    }

    companion object {
        private const val FUNDING_ID = 12L
        private const val OTHER_FUNDING_ID = 13L
        private const val CONTRIBUTION_ID = 31L
        private const val OTHER_CONTRIBUTION_ID = 32L
        private const val ACCESS_TOKEN = "guest-access-token"
        private val NOW = LocalDateTime.of(2026, 11, 18, 12, 0)
        private val TOKEN_EXPIRES_AT = Instant.parse("2026-11-18T03:10:00Z")
    }
}
