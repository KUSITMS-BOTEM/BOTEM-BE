package com.botem.domain.funding.service

import com.botem.domain.account.entity.Bank
import com.botem.domain.funding.dto.request.SendSurpriseThankCardRequest
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.entity.GiftVerification
import com.botem.domain.funding.entity.SurpriseThankCard
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.GiftVerificationRepository
import com.botem.domain.funding.repository.RollingPaperMessageRepository
import com.botem.domain.funding.repository.SurpriseThankCardRepository
import com.botem.domain.funding.repository.projection.FundingContributionSummary
import com.botem.domain.funding.repository.projection.RollingPaperMessageProjection
import com.botem.domain.user.entity.User
import com.botem.domain.user.repository.UserRepository
import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
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

class SurpriseRevealServiceTest {

    private val fundingRepository = mock(FundingRepository::class.java)
    private val giftVerificationRepository = mock(GiftVerificationRepository::class.java)
    private val contributionRepository = mock(ContributionRepository::class.java)
    private val rollingPaperMessageRepository = mock(RollingPaperMessageRepository::class.java)
    private val surpriseThankCardRepository = mock(SurpriseThankCardRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val service = SurpriseRevealService(
        fundingRepository = fundingRepository,
        giftVerificationRepository = giftVerificationRepository,
        contributionRepository = contributionRepository,
        rollingPaperMessageRepository = rollingPaperMessageRepository,
        surpriseThankCardRepository = surpriseThankCardRepository,
        userRepository = userRepository,
        clock = Clock.fixed(Instant.parse("2026-11-15T00:41:00Z"), ZoneId.of("Asia/Seoul")),
    )

    @Test
    fun `수신자가 공개된 서프라이즈 상세과 롤링페이퍼를 조회한다`() {
        val funding = funding(status = FundingStatus.WAITING_REVEAL)
        given(fundingRepository.findById(FUNDING_ID)).willReturn(Optional.of(funding))
        givenDetailData()

        val result = service.getDetail(RECIPIENT_ID, FUNDING_ID)

        assertEquals("지유", result.recipientName)
        assertEquals("민지", result.organizerName)
        assertEquals(429_000L, result.confirmedAmount)
        assertEquals(8L, result.participantCount)
        assertEquals(1, result.messageCount)
        assertEquals("하은", result.messages.single().participantName)
        assertEquals("https://image.example.com/profile.jpg", result.messages.single().participantProfileImageUrl)
        assertNull(result.openedAt)
        assertFalse(result.thankCardSent)
    }

    @Test
    fun `공개 전이거나 수신자가 아니면 상세를 조회할 수 없다`() {
        val beforeReveal = funding(revealAt = NOW.plusSeconds(1))
        given(fundingRepository.findById(FUNDING_ID)).willReturn(Optional.of(beforeReveal))

        val beforeRevealException = assertFailsWith<BusinessException> {
            service.getDetail(RECIPIENT_ID, FUNDING_ID)
        }
        assertEquals(FundingErrorCode.SURPRISE_REVEAL_NOT_AVAILABLE, beforeRevealException.errorCode)
        val hiddenFromOtherUser = assertFailsWith<BusinessException> {
            service.getDetail(RECIPIENT_ID + 1, FUNDING_ID)
        }
        assertEquals(FundingErrorCode.SURPRISE_REVEAL_NOT_AVAILABLE, hiddenFromOtherUser.errorCode)

        val revealed = funding()
        given(fundingRepository.findById(FUNDING_ID)).willReturn(Optional.of(revealed))
        val recipientException = assertFailsWith<BusinessException> {
            service.getDetail(RECIPIENT_ID + 1, FUNDING_ID)
        }
        assertEquals(FundingErrorCode.SURPRISE_RECIPIENT_ONLY, recipientException.errorCode)
        verify(userRepository, never()).findById(RECIPIENT_ID + 1)
    }

    @Test
    fun `존재하지 않는 서프라이즈는 조회하거나 열거나 감사 카드를 보낼 수 없다`() {
        given(fundingRepository.findById(FUNDING_ID)).willReturn(Optional.empty())
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(null)

        val detailException = assertFailsWith<BusinessException> {
            service.getDetail(RECIPIENT_ID, FUNDING_ID)
        }
        assertEquals(FundingErrorCode.FUNDING_NOT_FOUND, detailException.errorCode)

        val openException = assertFailsWith<BusinessException> {
            service.open(RECIPIENT_ID, FUNDING_ID)
        }
        assertEquals(FundingErrorCode.FUNDING_NOT_FOUND, openException.errorCode)

        val thankCardException = assertFailsWith<BusinessException> {
            service.sendThankCard(
                RECIPIENT_ID,
                FUNDING_ID,
                SendSurpriseThankCardRequest("고마워!"),
            )
        }
        assertEquals(FundingErrorCode.FUNDING_NOT_FOUND, thankCardException.errorCode)
    }

    @Test
    fun `참여 요약과 메시지가 없으면 빈 상세과 닉네임을 반환한다`() {
        val funding = funding()
        given(fundingRepository.findById(FUNDING_ID)).willReturn(Optional.of(funding))
        given(userRepository.findById(RECIPIENT_ID)).willReturn(
            Optional.of(user(RECIPIENT_ID, name = null, nickname = "수신자 닉네임")),
        )
        given(userRepository.findById(OWNER_ID)).willReturn(
            Optional.of(user(OWNER_ID, name = null, nickname = "주최자 닉네임")),
        )
        given(contributionRepository.summarizeByFundingIds(listOf(FUNDING_ID))).willReturn(emptyList())
        given(rollingPaperMessageRepository.findAllByFundingId(FUNDING_ID)).willReturn(emptyList())
        given(surpriseThankCardRepository.existsByFundingId(FUNDING_ID)).willReturn(true)

        val result = service.getDetail(RECIPIENT_ID, FUNDING_ID)

        assertEquals("수신자 닉네임", result.recipientName)
        assertEquals("주최자 닉네임", result.organizerName)
        assertEquals(0L, result.confirmedAmount)
        assertEquals(0L, result.participantCount)
        assertEquals(0, result.messageCount)
        assertTrue(result.thankCardSent)
    }

    @Test
    fun `수신자 또는 주최자 정보가 없으면 상세 조회를 중단한다`() {
        val funding = funding()
        given(fundingRepository.findById(FUNDING_ID)).willReturn(Optional.of(funding))
        given(userRepository.findById(RECIPIENT_ID)).willReturn(Optional.empty())

        val recipientException = assertFailsWith<BusinessException> {
            service.getDetail(RECIPIENT_ID, FUNDING_ID)
        }
        assertEquals(com.botem.domain.user.exception.UserErrorCode.USER_NOT_FOUND, recipientException.errorCode)

        given(userRepository.findById(RECIPIENT_ID)).willReturn(Optional.of(user(RECIPIENT_ID, "지유")))
        given(userRepository.findById(OWNER_ID)).willReturn(Optional.empty())
        val organizerException = assertFailsWith<BusinessException> {
            service.getDetail(RECIPIENT_ID, FUNDING_ID)
        }
        assertEquals(com.botem.domain.user.exception.UserErrorCode.USER_NOT_FOUND, organizerException.errorCode)
    }

    @Test
    fun `수신자가 서프라이즈를 열면 최초 열람 시각과 상태를 변경한다`() {
        val funding = funding(status = FundingStatus.WAITING_REVEAL)
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)
        givenDetailData()

        val result = service.open(RECIPIENT_ID, FUNDING_ID)

        assertEquals(NOW, result.openedAt)
        assertEquals(FundingStatus.REVEALED, funding.status)

        val retried = service.open(RECIPIENT_ID, FUNDING_ID)
        assertEquals(NOW, retried.openedAt)
        assertEquals(FundingStatus.REVEALED, funding.status)
    }

    @Test
    fun `열린 서프라이즈에 공백을 제거한 감사 카드를 저장한다`() {
        val funding = openedFunding()
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)
        given(surpriseThankCardRepository.findByFundingId(FUNDING_ID)).willReturn(null)
        given(surpriseThankCardRepository.save(org.mockito.ArgumentMatchers.any(SurpriseThankCard::class.java)))
            .willAnswer { invocation ->
                (invocation.arguments[0] as SurpriseThankCard).also { setField(it, "id", THANK_CARD_ID) }
            }
        given(giftVerificationRepository.save(org.mockito.ArgumentMatchers.any(GiftVerification::class.java)))
            .willAnswer { invocation -> invocation.arguments[0] as GiftVerification }

        val result = service.sendThankCard(
            RECIPIENT_ID,
            FUNDING_ID,
            SendSurpriseThankCardRequest("  정말 고마워!  "),
        )

        assertEquals(THANK_CARD_ID, result.thankCardId)
        assertEquals("정말 고마워!", result.message)
        assertEquals(NOW, result.sentAt)
        assertEquals(FundingStatus.WAITING_GIFT_VERIFICATION, funding.status)

        val captor = ArgumentCaptor.forClass(SurpriseThankCard::class.java)
        verify(surpriseThankCardRepository).save(captor.capture())
        assertEquals(RECIPIENT_ID, captor.value.senderUserId)
        assertEquals("정말 고마워!", captor.value.message)
        verify(giftVerificationRepository).findByFundingId(FUNDING_ID)
        verify(giftVerificationRepository).save(org.mockito.ArgumentMatchers.any(GiftVerification::class.java))
    }

    @Test
    fun `감사 카드 재요청은 최초 카드를 그대로 반환한다`() {
        val funding = openedFunding().also { it.beginGiftVerification() }
        val existing = thankCard("최초 메시지")
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)
        given(surpriseThankCardRepository.findByFundingId(FUNDING_ID)).willReturn(existing)

        val result = service.sendThankCard(
            RECIPIENT_ID,
            FUNDING_ID,
            SendSurpriseThankCardRequest("변경할 메시지"),
        )

        assertEquals("최초 메시지", result.message)
        verify(surpriseThankCardRepository, never()).save(org.mockito.ArgumentMatchers.any())
        verifyNoInteractions(giftVerificationRepository)
    }

    @Test
    fun `기존 선물 인증이 있으면 감사 카드 전송 시 중복 생성하지 않는다`() {
        val funding = openedFunding()
        val existingVerification = GiftVerification(FUNDING_ID)
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)
        given(surpriseThankCardRepository.findByFundingId(FUNDING_ID)).willReturn(null)
        given(surpriseThankCardRepository.save(org.mockito.ArgumentMatchers.any(SurpriseThankCard::class.java)))
            .willAnswer { invocation ->
                (invocation.arguments[0] as SurpriseThankCard).also { setField(it, "id", THANK_CARD_ID) }
            }
        given(giftVerificationRepository.findByFundingId(FUNDING_ID)).willReturn(existingVerification)

        service.sendThankCard(
            RECIPIENT_ID,
            FUNDING_ID,
            SendSurpriseThankCardRequest("고마워!"),
        )

        assertEquals(FundingStatus.WAITING_GIFT_VERIFICATION, funding.status)
        verify(giftVerificationRepository, never()).save(org.mockito.ArgumentMatchers.any())
    }

    @Test
    fun `서프라이즈를 열기 전에는 감사 카드를 보낼 수 없다`() {
        val funding = funding(status = FundingStatus.WAITING_REVEAL)
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)

        val exception = assertFailsWith<BusinessException> {
            service.sendThankCard(
                RECIPIENT_ID,
                FUNDING_ID,
                SendSurpriseThankCardRequest("고마워!"),
            )
        }

        assertEquals(FundingErrorCode.SURPRISE_NOT_OPENED, exception.errorCode)
        verify(surpriseThankCardRepository, never()).findByFundingId(FUNDING_ID)
    }

    @Test
    fun `공백 또는 최대 길이를 넘은 감사 메시지는 저장하지 않는다`() {
        val funding = openedFunding()
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)
        given(surpriseThankCardRepository.findByFundingId(FUNDING_ID)).willReturn(null)

        listOf("\u00A0", "가".repeat(1001)).forEach { message ->
            val exception = assertFailsWith<BusinessException> {
                service.sendThankCard(
                    RECIPIENT_ID,
                    FUNDING_ID,
                    SendSurpriseThankCardRequest(message),
                )
            }
            assertEquals(CommonErrorCode.INVALID_INPUT_VALUE, exception.errorCode)
        }

        verify(surpriseThankCardRepository, never()).save(org.mockito.ArgumentMatchers.any())
    }

    private fun givenDetailData() {
        given(userRepository.findById(RECIPIENT_ID)).willReturn(Optional.of(user(RECIPIENT_ID, "지유")))
        given(userRepository.findById(OWNER_ID)).willReturn(Optional.of(user(OWNER_ID, "민지")))
        given(contributionRepository.summarizeByFundingIds(listOf(FUNDING_ID))).willReturn(
            listOf(
                FundingContributionSummary(
                    fundingId = FUNDING_ID,
                    confirmedAmount = 429_000L,
                    pendingAmount = 0L,
                    pendingCount = 0L,
                    participantCount = 8L,
                ),
            ),
        )
        given(rollingPaperMessageRepository.findAllByFundingId(FUNDING_ID)).willReturn(
            listOf(
                RollingPaperMessageProjection(
                    messageId = 1L,
                    participantName = "하은",
                    participantProfileImageUrl = "https://image.example.com/profile.jpg",
                    content = "생일 축하해!",
                    paperTemplateCode = "BLUE_DOT_GRID",
                    decorationCode = null,
                    photoUrl = null,
                    voiceUrl = null,
                    voiceDurationSeconds = null,
                    voiceTranscript = null,
                ),
            ),
        )
        given(surpriseThankCardRepository.existsByFundingId(FUNDING_ID)).willReturn(false)
    }

    private fun funding(
        status: FundingStatus = FundingStatus.WAITING_REVEAL,
        revealAt: LocalDateTime = NOW.minusSeconds(1),
    ): Funding =
        Funding(
            ownerId = OWNER_ID,
            recipientId = RECIPIENT_ID,
            type = FundingType.SURPRISE,
            title = "몰래 준비한 선물",
            targetAmount = 500_000L,
            deadline = NOW.minusDays(1),
            revealAt = revealAt,
            story = null,
            promise = null,
            product = FundingProductSnapshot(
                sourceWishId = null,
                name = "무선 헤드폰",
                imageUrl = "https://image.example.com/product.jpg",
                price = 500_000L,
                url = "https://shop.example.com/product",
            ),
            payoutAccount = FundingPayoutAccountSnapshot(
                sourcePayoutAccountId = null,
                bank = Bank.KAKAO_BANK,
                encryptedAccountNumber = "encrypted",
                accountHolder = "민지",
            ),
        ).also {
            setField(it, "id", FUNDING_ID)
            setField(it, "status", status)
        }

    private fun openedFunding(): Funding =
        funding().also { it.revealSurprise(RECIPIENT_ID, NOW) }

    private fun user(
        id: Long,
        name: String?,
        nickname: String = "nickname-$id",
    ): User =
        User(
            kakaoId = id,
            nickname = nickname,
            profileImageUrl = null,
            name = name,
        ).also { setField(it, "id", id) }

    private fun thankCard(message: String): SurpriseThankCard =
        SurpriseThankCard(FUNDING_ID, RECIPIENT_ID, message, NOW.minusMinutes(1))
            .also { setField(it, "id", THANK_CARD_ID) }

    private fun setField(target: Any, fieldName: String, value: Any) {
        target.javaClass.getDeclaredField(fieldName).apply {
            isAccessible = true
            set(target, value)
        }
    }

    companion object {
        private const val OWNER_ID = 1L
        private const val RECIPIENT_ID = 2L
        private const val FUNDING_ID = 12L
        private const val THANK_CARD_ID = 7L
        private val NOW = LocalDateTime.of(2026, 11, 15, 9, 41)
    }
}
