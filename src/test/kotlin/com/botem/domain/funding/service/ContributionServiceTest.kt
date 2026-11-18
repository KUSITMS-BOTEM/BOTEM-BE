package com.botem.domain.funding.service

import com.botem.domain.account.entity.Bank
import com.botem.domain.account.service.AccountNumberCipher
import com.botem.domain.funding.dto.request.CreateContributionRequest
import com.botem.domain.funding.dto.request.CreateMessageContributionRequest
import com.botem.domain.funding.dto.request.MarkTransferSentRequest
import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.entity.RollingPaperMessage
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.RollingPaperMessageRepository
import com.botem.domain.user.entity.User
import com.botem.domain.user.repository.UserRepository
import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import com.botem.global.property.PayoutAccountEncryptionProperties
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContributionServiceTest {

    private val fundingRepository = mock(FundingRepository::class.java)
    private val contributionRepository = mock(ContributionRepository::class.java)
    private val rollingPaperMessageRepository = mock(RollingPaperMessageRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val guestPasswordVerifier = mock(GuestContributionPasswordVerifier::class.java)
    private val cipher = AccountNumberCipher(
        PayoutAccountEncryptionProperties(key = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="),
    )
    private val service = ContributionService(
        fundingRepository = fundingRepository,
        contributionRepository = contributionRepository,
        rollingPaperMessageRepository = rollingPaperMessageRepository,
        userRepository = userRepository,
        accountNumberCipher = cipher,
        guestPasswordVerifier = guestPasswordVerifier,
        clock = Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), ZoneId.of("Asia/Seoul")),
    )

    @Test
    fun `비회원이 메시지와 함께 보태면 송금 대기로 저장하고 비밀번호 해시와 받을 계좌를 반환한다`() {
        givenFunding(confirmedAmount = 0L)
        givenSaveReturnsArgument()

        val response = service.contribute(null, FUNDING_ID, request(message = "  생일 축하해!  "))

        val contribution = captureSavedContribution()
        assertEquals(ContributionStatus.AWAITING_TRANSFER, contribution.status)
        assertEquals("민지", contribution.participantName)
        assertNull(contribution.participantUserId)
        assertTrue(BCryptPasswordEncoder().matches("1234", contribution.guestPasswordHash))
        assertEquals(REQUEST_KEY, contribution.requestKey)

        val messageCaptor = ArgumentCaptor.forClass(RollingPaperMessage::class.java)
        verify(rollingPaperMessageRepository).save(messageCaptor.capture())
        assertEquals("생일 축하해!", messageCaptor.value.content)
        assertEquals("BLUE_DOT_GRID", messageCaptor.value.paperTemplateCode)

        assertEquals(Bank.TOSS_BANK, response.payoutAccount.bank)
        assertEquals("토스뱅크", response.payoutAccount.bankName)
        assertEquals(ACCOUNT_NUMBER, response.payoutAccount.accountNumber)
        assertEquals("김지유", response.payoutAccount.accountHolder)
    }

    @Test
    fun `로그인 회원이 이름을 생략하면 닉네임을 사용하고 비밀번호는 저장하지 않는다`() {
        givenFunding(confirmedAmount = 0L)
        givenSaveReturnsArgument()
        given(userRepository.findById(USER_ID))
            .willReturn(Optional.of(User(kakaoId = 1L, nickname = "지유친구", profileImageUrl = null)))

        service.contribute(
            USER_ID,
            FUNDING_ID,
            request(participantName = " ", guestPassword = null),
        )

        val contribution = captureSavedContribution()
        assertEquals("지유친구", contribution.participantName)
        assertEquals(USER_ID, contribution.participantUserId)
        assertNull(contribution.guestPasswordHash)
    }

    @Test
    fun `비회원은 이름과 숫자 4자리 비밀번호가 필수다`() {
        givenFunding(confirmedAmount = 0L)

        assertInvalidInput { service.contribute(null, FUNDING_ID, request(participantName = null)) }
        assertInvalidInput { service.contribute(null, FUNDING_ID, request(guestPassword = null)) }
        assertInvalidInput { service.contribute(null, FUNDING_ID, request(guestPassword = "12a4")) }
    }

    @Test
    fun `같은 펀딩에서 사용 중인 비회원 이름은 비밀번호가 달라도 다시 사용할 수 없다`() {
        givenFunding(confirmedAmount = 0L)
        given(
            contributionRepository.findAllByFundingIdAndParticipantUserIdIsNullAndParticipantNameAndStatusNot(
                FUNDING_ID,
                "민지",
                ContributionStatus.CANCELLED,
            ),
        ).willReturn(listOf(contribution(participantUserId = null)))

        assertErrorCode(FundingErrorCode.CONTRIBUTION_GUEST_IDENTITY_DUPLICATED) {
            service.contribute(null, FUNDING_ID, request(guestPassword = "9999"))
        }
        assertErrorCode(FundingErrorCode.CONTRIBUTION_GUEST_IDENTITY_DUPLICATED) {
            service.leaveMessage(null, FUNDING_ID, messageRequest(guestPassword = "9999"))
        }
        verify(contributionRepository, never()).save(any(Contribution::class.java))
    }

    @Test
    fun `축하 메시지와 편지지는 공백만으로 채울 수 없다`() {
        givenFunding(confirmedAmount = 0L)

        assertInvalidInput { service.contribute(null, FUNDING_ID, request(message = "\u3000 ")) }
        assertInvalidInput { service.contribute(null, FUNDING_ID, request(paperTemplateCode = " ")) }
        verify(contributionRepository, never()).save(any(Contribution::class.java))
    }

    @Test
    fun `남은 목표 금액과 같은 금액은 보탤 수 있고 초과하면 거절한다`() {
        givenFunding(confirmedAmount = 70_000L)
        givenSaveReturnsArgument()

        service.contribute(null, FUNDING_ID, request(amount = 30_000L))

        val exception = assertFailsWith<BusinessException> {
            service.contribute(null, FUNDING_ID, request(amount = 30_001L))
        }
        assertEquals(FundingErrorCode.CONTRIBUTION_AMOUNT_EXCEEDS_REMAINING, exception.errorCode)
        assertEquals("30000", exception.detail)
    }

    @Test
    fun `확정 금액이 목표 금액에 도달한 펀딩에는 보탤 수 없다`() {
        givenFunding(confirmedAmount = TARGET_AMOUNT)

        assertErrorCode(FundingErrorCode.FUNDING_TARGET_REACHED) {
            service.contribute(null, FUNDING_ID, request(amount = 1L))
        }
    }

    @Test
    fun `마감된 펀딩에는 보탤 수 없다`() {
        givenFunding(confirmedAmount = 0L, deadline = NOW)

        assertErrorCode(FundingErrorCode.FUNDING_CLOSED) {
            service.contribute(null, FUNDING_ID, request())
        }
        verify(contributionRepository, never()).save(any(Contribution::class.java))
    }

    @Test
    fun `같은 요청 키로 다시 요청하면 새로 만들지 않고 기존 참여를 반환한다`() {
        givenFunding(confirmedAmount = TARGET_AMOUNT)
        val existing = contribution(participantUserId = null)
        given(contributionRepository.findByFundingIdAndRequestKey(FUNDING_ID, REQUEST_KEY)).willReturn(existing)

        val response = service.contribute(null, FUNDING_ID, request())

        assertEquals(existing.amount, response.amount)
        assertEquals(ContributionStatus.AWAITING_TRANSFER, response.status)
        verify(contributionRepository, never()).save(any(Contribution::class.java))
    }

    @Test
    fun `같은 요청 키로 금액을 바꿔 보내면 거절한다`() {
        givenFunding(confirmedAmount = 0L)
        given(contributionRepository.findByFundingIdAndRequestKey(FUNDING_ID, REQUEST_KEY))
            .willReturn(contribution(participantUserId = null))

        assertErrorCode(FundingErrorCode.CONTRIBUTION_REQUEST_KEY_CONFLICT) {
            service.contribute(null, FUNDING_ID, request(amount = 20_000L))
        }
    }

    @Test
    fun `다른 참여자가 쓴 요청 키는 거절한다`() {
        givenFunding(confirmedAmount = 0L)
        given(contributionRepository.findByFundingIdAndRequestKey(FUNDING_ID, REQUEST_KEY))
            .willReturn(contribution(participantUserId = OTHER_USER_ID))

        assertErrorCode(FundingErrorCode.CONTRIBUTION_REQUEST_KEY_CONFLICT) {
            service.contribute(USER_ID, FUNDING_ID, request())
        }
    }

    @Test
    fun `비회원이 메시지로 보태면 0원 메시지 전용으로 저장하고 롤링페이퍼를 만든다`() {
        givenFunding(confirmedAmount = 0L)
        givenSaveReturnsArgument()

        val response = service.leaveMessage(null, FUNDING_ID, messageRequest())

        val contribution = captureSavedContribution()
        assertEquals(0L, contribution.amount)
        assertEquals(ContributionStatus.MESSAGE_ONLY, contribution.status)
        assertEquals("민지", contribution.participantName)
        assertTrue(BCryptPasswordEncoder().matches("1234", contribution.guestPasswordHash))
        assertEquals(ContributionStatus.MESSAGE_ONLY, response.status)

        val messageCaptor = ArgumentCaptor.forClass(RollingPaperMessage::class.java)
        verify(rollingPaperMessageRepository).save(messageCaptor.capture())
        assertEquals("다 모였네! 축하해", messageCaptor.value.content)
    }

    @Test
    fun `로그인 회원이 이름을 생략하고 메시지로 보태면 닉네임을 사용한다`() {
        givenFunding(confirmedAmount = 0L)
        givenSaveReturnsArgument()
        given(userRepository.findById(USER_ID))
            .willReturn(Optional.of(User(kakaoId = 1L, nickname = "지유친구", profileImageUrl = null)))

        service.leaveMessage(USER_ID, FUNDING_ID, messageRequest(participantName = null, guestPassword = null))

        val contribution = captureSavedContribution()
        assertEquals("지유친구", contribution.participantName)
        assertNull(contribution.guestPasswordHash)
    }

    @Test
    fun `목표 금액을 달성한 뒤에도 마감 전이면 메시지로 보탤 수 있다`() {
        givenFunding(confirmedAmount = TARGET_AMOUNT + 5_000L)
        givenSaveReturnsArgument()

        val response = service.leaveMessage(null, FUNDING_ID, messageRequest())

        assertEquals(ContributionStatus.MESSAGE_ONLY, response.status)
    }

    @Test
    fun `마감된 펀딩에는 메시지로 보탤 수 없다`() {
        givenFunding(confirmedAmount = 0L, deadline = NOW)

        assertErrorCode(FundingErrorCode.FUNDING_CLOSED) {
            service.leaveMessage(null, FUNDING_ID, messageRequest())
        }
        verify(contributionRepository, never()).save(any(Contribution::class.java))
    }

    @Test
    fun `메시지로 보탤 때도 비회원 이름과 비밀번호, 메시지가 필수다`() {
        givenFunding(confirmedAmount = 0L)

        assertInvalidInput { service.leaveMessage(null, FUNDING_ID, messageRequest(participantName = " ")) }
        assertInvalidInput { service.leaveMessage(null, FUNDING_ID, messageRequest(guestPassword = null)) }
        assertInvalidInput { service.leaveMessage(null, FUNDING_ID, messageRequest(message = "\u3000")) }
        verify(contributionRepository, never()).save(any(Contribution::class.java))
    }

    @Test
    fun `같은 요청 키로 다시 메시지를 보내면 기존 참여를 반환한다`() {
        givenFunding(confirmedAmount = 0L)
        val existing = Contribution(
            fundingId = FUNDING_ID,
            participantUserId = null,
            participantName = "민지",
            amount = 0L,
            status = ContributionStatus.MESSAGE_ONLY,
            guestPasswordHash = null,
            requestKey = REQUEST_KEY,
        )
        given(contributionRepository.findByFundingIdAndRequestKey(FUNDING_ID, REQUEST_KEY)).willReturn(existing)

        val response = service.leaveMessage(null, FUNDING_ID, messageRequest())

        assertEquals(ContributionStatus.MESSAGE_ONLY, response.status)
        verify(contributionRepository, never()).save(any(Contribution::class.java))
    }

    @Test
    fun `금액 보태기에 쓴 요청 키로 메시지를 보내면 거절한다`() {
        givenFunding(confirmedAmount = 0L)
        given(contributionRepository.findByFundingIdAndRequestKey(FUNDING_ID, REQUEST_KEY))
            .willReturn(contribution(participantUserId = null))

        assertErrorCode(FundingErrorCode.CONTRIBUTION_REQUEST_KEY_CONFLICT) {
            service.leaveMessage(null, FUNDING_ID, messageRequest())
        }
    }

    @Test
    fun `비회원이 맞는 비밀번호로 송금 완료를 표시하면 확인 대기로 바뀐다`() {
        val contribution = givenContribution(participantUserId = null)
        given(guestPasswordVerifier.authenticate("1234", listOf(contribution))).willReturn(contribution)

        val response = service.markTransferSent(null, FUNDING_ID, CONTRIBUTION_ID, MarkTransferSentRequest("1234"))

        assertEquals(ContributionStatus.PENDING_CONFIRMATION, response.status)
        assertEquals(ContributionStatus.PENDING_CONFIRMATION, contribution.status)
    }

    @Test
    fun `비회원 비밀번호가 틀리거나 없으면 송금 완료를 표시할 수 없다`() {
        val contribution = givenContribution(participantUserId = null)
        given(guestPasswordVerifier.authenticate("9999", listOf(contribution)))
            .willThrow(BusinessException(FundingErrorCode.CONTRIBUTION_PASSWORD_MISMATCH))
        given(guestPasswordVerifier.authenticate(null, listOf(contribution)))
            .willThrow(BusinessException(FundingErrorCode.CONTRIBUTION_PASSWORD_MISMATCH))

        assertErrorCode(FundingErrorCode.CONTRIBUTION_PASSWORD_MISMATCH) {
            service.markTransferSent(null, FUNDING_ID, CONTRIBUTION_ID, MarkTransferSentRequest("9999"))
        }
        assertErrorCode(FundingErrorCode.CONTRIBUTION_PASSWORD_MISMATCH) {
            service.markTransferSent(null, FUNDING_ID, CONTRIBUTION_ID, MarkTransferSentRequest(null))
        }
        assertEquals(ContributionStatus.AWAITING_TRANSFER, contribution.status)
    }

    @Test
    fun `회원 참여는 본인만 송금 완료를 표시할 수 있다`() {
        givenContribution(participantUserId = USER_ID)

        assertErrorCode(FundingErrorCode.CONTRIBUTION_PARTICIPANT_ONLY) {
            service.markTransferSent(OTHER_USER_ID, FUNDING_ID, CONTRIBUTION_ID, MarkTransferSentRequest())
        }
        assertErrorCode(FundingErrorCode.CONTRIBUTION_PARTICIPANT_ONLY) {
            service.markTransferSent(null, FUNDING_ID, CONTRIBUTION_ID, MarkTransferSentRequest())
        }

        val response = service.markTransferSent(USER_ID, FUNDING_ID, CONTRIBUTION_ID, MarkTransferSentRequest())
        assertEquals(ContributionStatus.PENDING_CONFIRMATION, response.status)
    }

    @Test
    fun `이미 송금 완료를 표시한 참여는 다시 요청해도 상태를 유지한다`() {
        val contribution = givenContribution(participantUserId = USER_ID)
        contribution.markTransferSent()

        val response = service.markTransferSent(USER_ID, FUNDING_ID, CONTRIBUTION_ID, MarkTransferSentRequest())

        assertEquals(ContributionStatus.PENDING_CONFIRMATION, response.status)
    }

    @Test
    fun `보태기 마감으로 메시지 전용이 된 참여는 송금 완료로 표시할 수 없다`() {
        givenFunding(confirmedAmount = 0L)
        given(contributionRepository.findByIdAndFundingIdForUpdate(CONTRIBUTION_ID, FUNDING_ID)).willReturn(
            Contribution(
                fundingId = FUNDING_ID,
                participantUserId = USER_ID,
                participantName = "민지",
                amount = 0L,
                status = ContributionStatus.MESSAGE_ONLY,
                guestPasswordHash = null,
            ),
        )

        assertErrorCode(FundingErrorCode.CONTRIBUTION_CONVERTED_TO_MESSAGE_ONLY) {
            service.markTransferSent(USER_ID, FUNDING_ID, CONTRIBUTION_ID, MarkTransferSentRequest())
        }
    }

    @Test
    fun `거절된 참여는 송금 완료로 표시할 수 없다`() {
        val contribution = givenContribution(participantUserId = USER_ID)
        contribution.markTransferSent()
        contribution.reject()

        assertErrorCode(FundingErrorCode.CONTRIBUTION_TRANSFER_NOT_MARKABLE) {
            service.markTransferSent(USER_ID, FUNDING_ID, CONTRIBUTION_ID, MarkTransferSentRequest())
        }
    }

    @Test
    fun `다른 펀딩의 참여는 찾을 수 없다`() {
        givenFunding(confirmedAmount = 0L)
        assertErrorCode(FundingErrorCode.CONTRIBUTION_NOT_FOUND) {
            service.markTransferSent(USER_ID, FUNDING_ID, CONTRIBUTION_ID, MarkTransferSentRequest())
        }
    }

    private fun givenFunding(confirmedAmount: Long, deadline: LocalDateTime = NOW.plusDays(3)) {
        val funding = Funding(
            ownerId = OTHER_USER_ID,
            recipientId = null,
            type = FundingType.BIRTHDAY,
            title = "생일 펀딩",
            targetAmount = TARGET_AMOUNT,
            deadline = deadline,
            revealAt = null,
            story = "사연",
            promise = null,
            product = FundingProductSnapshot(null, "에어팟", "https://image.example.com/p.jpg", 300_000L, "https://example.com/p"),
            payoutAccount = FundingPayoutAccountSnapshot(
                sourcePayoutAccountId = null,
                bank = Bank.TOSS_BANK,
                encryptedAccountNumber = cipher.encrypt(ACCOUNT_NUMBER),
                accountHolder = "김지유",
            ),
        )
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)
        given(contributionRepository.sumAmountByFundingIdAndStatus(funding.id, ContributionStatus.CONFIRMED))
            .willReturn(confirmedAmount)
    }

    private fun givenSaveReturnsArgument() {
        given(contributionRepository.save(any(Contribution::class.java))).willAnswer { it.arguments[0] }
    }

    private fun givenContribution(participantUserId: Long?): Contribution =
        contribution(participantUserId).also {
            givenFunding(confirmedAmount = 0L)
            given(contributionRepository.findByIdAndFundingIdForUpdate(CONTRIBUTION_ID, FUNDING_ID)).willReturn(it)
        }

    private fun contribution(participantUserId: Long?): Contribution =
        Contribution(
            fundingId = FUNDING_ID,
            participantUserId = participantUserId,
            participantName = "민지",
            amount = 10_000L,
            status = ContributionStatus.AWAITING_TRANSFER,
            guestPasswordHash = if (participantUserId == null) BCryptPasswordEncoder().encode("1234") else null,
            requestKey = REQUEST_KEY,
        )

    private fun captureSavedContribution(): Contribution {
        val captor = ArgumentCaptor.forClass(Contribution::class.java)
        verify(contributionRepository).save(captor.capture())
        return captor.value
    }

    private fun request(
        participantName: String? = "민지",
        amount: Long = 10_000L,
        message: String = "생일 축하해!",
        paperTemplateCode: String = "BLUE_DOT_GRID",
        guestPassword: String? = "1234",
    ) = CreateContributionRequest(
        requestKey = REQUEST_KEY,
        participantName = participantName,
        amount = amount,
        message = message,
        paperTemplateCode = paperTemplateCode,
        guestPassword = guestPassword,
    )

    private fun messageRequest(
        participantName: String? = "민지",
        message: String = "다 모였네! 축하해",
        guestPassword: String? = "1234",
    ) = CreateMessageContributionRequest(
        requestKey = REQUEST_KEY,
        participantName = participantName,
        message = message,
        paperTemplateCode = "BLUE_DOT_GRID",
        guestPassword = guestPassword,
    )

    private fun assertInvalidInput(block: () -> Unit) {
        assertEquals(CommonErrorCode.INVALID_INPUT_VALUE, assertFailsWith<BusinessException>(block = block).errorCode)
    }

    private fun assertErrorCode(errorCode: FundingErrorCode, block: () -> Unit) {
        assertEquals(errorCode, assertFailsWith<BusinessException>(block = block).errorCode)
    }

    companion object {
        private const val USER_ID = 1L
        private const val OTHER_USER_ID = 2L
        private const val FUNDING_ID = 10L
        private const val CONTRIBUTION_ID = 31L
        private const val TARGET_AMOUNT = 100_000L
        private const val REQUEST_KEY = "7f1c2a9e-3b4d-4e5f-8a6b-1c2d3e4f5a6b"
        private const val ACCOUNT_NUMBER = "100012345678"
        private val NOW = LocalDateTime.of(2026, 11, 18, 12, 0)
    }
}
