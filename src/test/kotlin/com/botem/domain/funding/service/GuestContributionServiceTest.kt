package com.botem.domain.funding.service

import com.botem.domain.account.entity.Bank
import com.botem.domain.funding.dto.request.AccessGuestContributionRequest
import com.botem.domain.funding.dto.request.UpdateGuestContributionRequest
import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.entity.RollingPaperAttachmentType
import com.botem.domain.funding.entity.RollingPaperMessage
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.RollingPaperMessageRepository
import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
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
    private val accessTokenProvider = mock(GuestContributionAccessTokenProvider::class.java)
    private val passwordVerifier = mock(GuestContributionPasswordVerifier::class.java)
    private val rollingPaperAttachmentService = mock(RollingPaperAttachmentService::class.java)
    private val service = GuestContributionService(
        fundingRepository = fundingRepository,
        contributionRepository = contributionRepository,
        rollingPaperMessageRepository = rollingPaperMessageRepository,
        accessTokenProvider = accessTokenProvider,
        passwordVerifier = passwordVerifier,
        rollingPaperAttachmentService = rollingPaperAttachmentService,
        clock = Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), ZoneId.of("Asia/Seoul")),
    )

    @Test
    fun `이름과 비밀번호가 일치하면 보탬을 조회하고 접근 토큰을 발급한다`() {
        val contribution = contribution()
        val message = message(
            decorationCode = "PARTY_POPPER",
            photoUrl = PHOTO_URL,
            voiceUrl = VOICE_URL,
            voiceDurationSeconds = 12,
        )
        givenFunding()
        givenGuestContributions("민지", listOf(contribution))
        given(passwordVerifier.authenticate("1234", listOf(contribution))).willReturn(contribution)
        given(rollingPaperMessageRepository.findByContributionId(CONTRIBUTION_ID)).willReturn(message)
        given(accessTokenProvider.issue(FUNDING_ID, CONTRIBUTION_ID))
            .willReturn(IssuedGuestContributionAccessToken(ACCESS_TOKEN, TOKEN_EXPIRES_AT))

        val response = service.access(FUNDING_ID, AccessGuestContributionRequest("  민지  ", "1234"))

        assertEquals(CONTRIBUTION_ID, response.contributionId)
        assertEquals("민지", response.participantName)
        assertEquals("생일 축하해!", response.message)
        assertEquals("PARTY_POPPER", response.decorationCode)
        assertEquals(PHOTO_URL, response.photoUrl)
        assertEquals(VOICE_URL, response.voiceUrl)
        assertEquals(12, response.voiceDurationSeconds)
        assertEquals(ACCESS_TOKEN, response.accessToken)
        assertEquals(TOKEN_EXPIRES_AT, response.accessTokenExpiresAt)
    }

    @Test
    fun `존재하지 않는 이름도 비밀번호 불일치와 같은 오류를 반환한다`() {
        givenFunding()
        givenGuestContributions("없는 이름", emptyList())
        given(passwordVerifier.authenticate("1234", emptyList()))
            .willThrow(BusinessException(FundingErrorCode.CONTRIBUTION_PASSWORD_MISMATCH))

        assertError(FundingErrorCode.CONTRIBUTION_PASSWORD_MISMATCH) {
            service.access(FUNDING_ID, AccessGuestContributionRequest("없는 이름", "1234"))
        }
    }

    @Test
    fun `롤링페이퍼가 없는 기존 보탬도 조회할 수 있다`() {
        val contribution = contribution()
        givenFunding()
        givenGuestContributions("민지", listOf(contribution))
        given(passwordVerifier.authenticate("1234", listOf(contribution))).willReturn(contribution)
        given(rollingPaperMessageRepository.findByContributionId(CONTRIBUTION_ID)).willReturn(null)
        given(accessTokenProvider.issue(FUNDING_ID, CONTRIBUTION_ID))
            .willReturn(IssuedGuestContributionAccessToken(ACCESS_TOKEN, TOKEN_EXPIRES_AT))

        val response = service.access(FUNDING_ID, AccessGuestContributionRequest("민지", "1234"))

        assertNull(response.message)
        assertNull(response.paperTemplateCode)
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
        assertNull(response.photoUrl)
    }

    @Test
    fun `비회원은 사진과 음성과 스티커를 수정할 수 있다`() {
        val contribution = contribution()
        val message = message()
        givenToken(contribution)
        given(rollingPaperMessageRepository.findByContributionId(CONTRIBUTION_ID)).willReturn(message)

        val response = service.update(
            FUNDING_ID,
            ACCESS_TOKEN,
            UpdateGuestContributionRequest(
                participantName = "민지",
                message = null,
                paperTemplateCode = "BLACK_CONFETTI",
                decorationCode = "HEART",
                photoUrl = PHOTO_URL,
                voiceUrl = VOICE_URL,
                voiceDurationSeconds = 12,
                voiceTranscript = "생일 축하해",
            ),
        )

        verify(rollingPaperAttachmentService).validateUploadedAttachment(
            FUNDING_ID,
            RollingPaperAttachmentType.PHOTO,
            PHOTO_URL,
        )
        verify(rollingPaperAttachmentService).validateUploadedAttachment(
            FUNDING_ID,
            RollingPaperAttachmentType.VOICE,
            VOICE_URL,
        )
        assertNull(response.message)
        assertEquals("HEART", response.decorationCode)
        assertEquals(PHOTO_URL, response.photoUrl)
        assertEquals(VOICE_URL, response.voiceUrl)
        assertEquals(12, response.voiceDurationSeconds)
        assertEquals("생일 축하해", response.voiceTranscript)
    }

    @Test
    fun `롤링페이퍼 콘텐츠가 모두 비거나 음성 입력 조합이 다르면 수정할 수 없다`() {
        val contribution = contribution()
        givenToken(contribution)

        assertInvalidInput {
            service.update(
                FUNDING_ID,
                ACCESS_TOKEN,
                UpdateGuestContributionRequest("민지", null, "BLUE_DOT_GRID"),
            )
        }
        assertInvalidInput {
            service.update(
                FUNDING_ID,
                ACCESS_TOKEN,
                UpdateGuestContributionRequest(
                    participantName = "민지",
                    message = "축하해",
                    paperTemplateCode = "BLUE_DOT_GRID",
                    voiceUrl = VOICE_URL,
                ),
            )
        }
        assertInvalidInput {
            service.update(
                FUNDING_ID,
                ACCESS_TOKEN,
                UpdateGuestContributionRequest(
                    participantName = "민지",
                    message = "축하해",
                    paperTemplateCode = "BLUE_DOT_GRID",
                    voiceDurationSeconds = 0,
                ),
            )
        }
        assertInvalidInput {
            service.update(
                FUNDING_ID,
                ACCESS_TOKEN,
                UpdateGuestContributionRequest(
                    participantName = "민지",
                    message = "축하해",
                    paperTemplateCode = "BLUE_DOT_GRID",
                    voiceTranscript = "음성 없는 자막",
                ),
            )
        }
    }

    @Test
    fun `비회원 보탬도 일반 펀딩은 마감 전까지만 수정할 수 있다`() {
        val contribution = contribution()
        given(accessTokenProvider.parse(ACCESS_TOKEN))
            .willReturn(GuestContributionAccessPrincipal(FUNDING_ID, CONTRIBUTION_ID))
        given(contributionRepository.findByIdAndFundingIdForUpdate(CONTRIBUTION_ID, FUNDING_ID)).willReturn(contribution)
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding(deadline = NOW))

        assertError(FundingErrorCode.CONTRIBUTION_NOT_EDITABLE) {
            service.update(FUNDING_ID, ACCESS_TOKEN, UpdateGuestContributionRequest("민지", "메시지", "BLUE_DOT_GRID"))
        }
        verify(rollingPaperMessageRepository, never()).findByContributionId(CONTRIBUTION_ID)
    }

    @Test
    fun `조기 마감한 일반 펀딩은 원래 마감일 전까지 비회원 보탬을 수정할 수 있다`() {
        val contribution = contribution()
        val earlyClosed = funding().also { it.closeEarly(NOW) }
        given(accessTokenProvider.parse(ACCESS_TOKEN))
            .willReturn(GuestContributionAccessPrincipal(FUNDING_ID, CONTRIBUTION_ID))
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(earlyClosed)
        given(contributionRepository.findByIdAndFundingIdForUpdate(CONTRIBUTION_ID, FUNDING_ID)).willReturn(contribution)
        given(rollingPaperMessageRepository.findByContributionId(CONTRIBUTION_ID)).willReturn(message())

        val response = service.update(
            FUNDING_ID,
            ACCESS_TOKEN,
            UpdateGuestContributionRequest("민지", "수정 메시지", "BLUE_DOT_GRID"),
        )

        assertEquals("수정 메시지", response.message)
    }

    @Test
    fun `선물 준비 상태의 서프라이즈 펀딩에서는 비회원 보탬을 수정할 수 없다`() {
        val contribution = contribution()
        given(accessTokenProvider.parse(ACCESS_TOKEN))
            .willReturn(GuestContributionAccessPrincipal(FUNDING_ID, CONTRIBUTION_ID))
        given(fundingRepository.findByIdForUpdate(FUNDING_ID))
            .willReturn(funding(status = FundingStatus.GIFT_PREPARING))
        given(contributionRepository.findByIdAndFundingIdForUpdate(CONTRIBUTION_ID, FUNDING_ID)).willReturn(contribution)

        assertError(FundingErrorCode.CONTRIBUTION_NOT_EDITABLE) {
            service.update(FUNDING_ID, ACCESS_TOKEN, UpdateGuestContributionRequest("민지", "메시지", "BLUE_DOT_GRID"))
        }
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
    fun `롤링페이퍼가 없는 기존 보탬을 수정하면 새 행을 만든다`() {
        val contribution = contribution()
        givenToken(contribution)
        given(rollingPaperMessageRepository.findByContributionId(CONTRIBUTION_ID)).willReturn(null)
        given(rollingPaperMessageRepository.save(any(RollingPaperMessage::class.java))).willAnswer { it.arguments[0] }

        val response = service.update(
            FUNDING_ID,
            ACCESS_TOKEN,
            UpdateGuestContributionRequest("민지", "새 메시지", "BLUE_DOT_GRID"),
        )

        assertEquals("새 메시지", response.message)
        verify(rollingPaperMessageRepository).save(any(RollingPaperMessage::class.java))
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
    fun `참여자 이름과 비밀번호 형식을 서비스에서도 검증한다`() {
        assertInvalidInput {
            service.access(FUNDING_ID, AccessGuestContributionRequest(" ", "1234"))
        }
        assertInvalidInput {
            service.access(FUNDING_ID, AccessGuestContributionRequest("민지", "12a4"))
        }
    }

    @Test
    fun `존재하지 않거나 취소된 펀딩의 비회원 보탬은 조회할 수 없다`() {
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(null)

        assertError(FundingErrorCode.FUNDING_NOT_FOUND) {
            service.access(FUNDING_ID, AccessGuestContributionRequest("민지", "1234"))
        }
    }

    @Test
    fun `접근 토큰의 보탬이 없으면 수정할 수 없다`() {
        givenFunding()
        given(accessTokenProvider.parse(ACCESS_TOKEN))
            .willReturn(GuestContributionAccessPrincipal(FUNDING_ID, CONTRIBUTION_ID))
        given(contributionRepository.findByIdAndFundingIdForUpdate(CONTRIBUTION_ID, FUNDING_ID)).willReturn(null)

        assertError(FundingErrorCode.CONTRIBUTION_NOT_FOUND) {
            service.update(
                FUNDING_ID,
                ACCESS_TOKEN,
                UpdateGuestContributionRequest("민지", "메시지", "BLUE_DOT_GRID"),
            )
        }
    }

    @Test
    fun `로그인 회원의 보탬에는 비회원 접근 토큰을 사용할 수 없다`() {
        givenToken(contribution(participantUserId = 1L))

        assertError(FundingErrorCode.CONTRIBUTION_GUEST_ACCESS_TOKEN_INVALID) {
            service.update(
                FUNDING_ID,
                ACCESS_TOKEN,
                UpdateGuestContributionRequest("민지", "메시지", "BLUE_DOT_GRID"),
            )
        }
    }

    @Test
    fun `수정할 메시지와 편지지는 공백이거나 최대 길이를 넘을 수 없다`() {
        val contribution = contribution()
        givenToken(contribution)

        assertInvalidInput {
            service.update(
                FUNDING_ID,
                ACCESS_TOKEN,
                UpdateGuestContributionRequest("민지", " ", "BLUE_DOT_GRID"),
            )
        }
        assertInvalidInput {
            service.update(
                FUNDING_ID,
                ACCESS_TOKEN,
                UpdateGuestContributionRequest("민지", "메시지", "A".repeat(51)),
            )
        }
        assertInvalidInput {
            service.update(
                FUNDING_ID,
                ACCESS_TOKEN,
                UpdateGuestContributionRequest(
                    participantName = "민지",
                    message = "가".repeat(461),
                    paperTemplateCode = "BLUE_DOT_GRID",
                ),
            )
        }
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
        givenFunding()
        given(accessTokenProvider.parse(ACCESS_TOKEN))
            .willReturn(GuestContributionAccessPrincipal(FUNDING_ID, CONTRIBUTION_ID))
        given(contributionRepository.findByIdAndFundingIdForUpdate(CONTRIBUTION_ID, FUNDING_ID))
            .willReturn(contribution)
    }

    private fun funding(
        deadline: LocalDateTime = NOW.plusDays(3),
        status: FundingStatus = FundingStatus.FUNDING,
    ) = Funding(
        ownerId = 1L,
        recipientId = null,
        type = FundingType.BIRTHDAY,
        title = "생일 펀딩",
        targetAmount = 100_000L,
        deadline = deadline,
        revealAt = null,
        story = "사연",
        promise = null,
        product = FundingProductSnapshot(null, "에어팟", "https://example.com/p.jpg", 300_000L, "https://example.com/p"),
        payoutAccount = FundingPayoutAccountSnapshot(null, Bank.TOSS_BANK, "encrypted", "김지유"),
    ).also {
        setField(it, "id", FUNDING_ID)
        setField(it, "status", status)
    }

    private fun contribution(
        id: Long = CONTRIBUTION_ID,
        participantName: String = "민지",
        status: ContributionStatus = ContributionStatus.AWAITING_TRANSFER,
        participantUserId: Long? = null,
    ) = Contribution(
        fundingId = FUNDING_ID,
        participantUserId = participantUserId,
        participantName = participantName,
        amount = 10_000L,
        status = status,
        guestPasswordHash = BCryptPasswordEncoder().encode("1234"),
        requestKey = "request-$id",
    ).also { setField(it, "id", id) }

    private fun message(
        decorationCode: String? = null,
        photoUrl: String? = null,
        voiceUrl: String? = null,
        voiceDurationSeconds: Int? = null,
    ) = RollingPaperMessage(
        contributionId = CONTRIBUTION_ID,
        content = "생일 축하해!",
        paperTemplateCode = "BLUE_DOT_GRID",
        decorationCode = decorationCode,
        photoUrl = photoUrl,
        voiceUrl = voiceUrl,
        voiceDurationSeconds = voiceDurationSeconds,
    )

    private fun assertError(errorCode: FundingErrorCode, block: () -> Unit) {
        val exception = assertFailsWith<BusinessException>(block = block)
        assertEquals(errorCode, exception.errorCode)
    }

    private fun assertInvalidInput(block: () -> Unit) {
        val exception = assertFailsWith<BusinessException>(block = block)
        assertEquals(CommonErrorCode.INVALID_INPUT_VALUE, exception.errorCode)
    }

    companion object {
        private const val FUNDING_ID = 12L
        private const val OTHER_FUNDING_ID = 13L
        private const val CONTRIBUTION_ID = 31L
        private const val OTHER_CONTRIBUTION_ID = 32L
        private const val ACCESS_TOKEN = "guest-access-token"
        private const val PHOTO_URL =
            "https://botem-test.s3.ap-northeast-2.amazonaws.com/rolling-paper/12/photo/3f2b8c1e-7a4d-4e9b-9c2a-1d5e6f7a8b9c.jpg"
        private const val VOICE_URL =
            "https://botem-test.s3.ap-northeast-2.amazonaws.com/rolling-paper/12/voice/8a7b6c5d-4e3f-2a1b-9c8d-7e6f5a4b3c2d.m4a"
        private val NOW = LocalDateTime.of(2026, 11, 18, 12, 0)
        private val TOKEN_EXPIRES_AT = Instant.parse("2026-11-18T03:10:00Z")
    }
}
