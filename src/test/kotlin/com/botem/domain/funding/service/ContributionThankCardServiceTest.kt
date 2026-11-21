package com.botem.domain.funding.service

import com.botem.domain.account.entity.Bank
import com.botem.domain.funding.dto.request.SendContributionThankCardRequest
import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.entity.ContributionThankCard
import com.botem.domain.funding.entity.ThankCardColor
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.entity.RollingPaperMessage
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.dto.response.ContributionThankCardSenderRole
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.ContributionThankCardRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.RollingPaperMessageRepository
import com.botem.domain.user.entity.User
import com.botem.domain.user.repository.UserRepository
import com.botem.global.entity.BaseEntity
import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import com.botem.global.property.ContributionThankCardProperties
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContributionThankCardServiceTest {

    private val fundingRepository = mock(FundingRepository::class.java)
    private val contributionRepository = mock(ContributionRepository::class.java)
    private val thankCardRepository = mock(ContributionThankCardRepository::class.java)
    private val rollingPaperMessageRepository = mock(RollingPaperMessageRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val service = ContributionThankCardService(
        fundingRepository = fundingRepository,
        contributionRepository = contributionRepository,
        contributionThankCardRepository = thankCardRepository,
        rollingPaperMessageRepository = rollingPaperMessageRepository,
        userRepository = userRepository,
        contributionThankCardProperties = ContributionThankCardProperties("https://botem.me/thank-cards/"),
        clock = Clock.fixed(Instant.parse("2026-11-19T11:00:00Z"), ZoneId.of("Asia/Seoul")),
    )

    @Test
    fun `개설자가 확정 보탬에 공백을 제거한 감사카드를 보낸다`() {
        givenSendableFundingAndContribution()
        given(thankCardRepository.findByContributionId(CONTRIBUTION_ID)).willReturn(null)
        given(thankCardRepository.save(any(ContributionThankCard::class.java))).willAnswer { invocation ->
            (invocation.arguments[0] as ContributionThankCard).withId(THANK_CARD_ID)
        }

        val response = service.send(
            OWNER_ID,
            FUNDING_ID,
            CONTRIBUTION_ID,
            SendContributionThankCardRequest("  정말 고마워!  ", ThankCardColor.BLUE),
        )

        val captor = ArgumentCaptor.forClass(ContributionThankCard::class.java)
        verify(thankCardRepository).save(captor.capture())
        assertEquals("정말 고마워!", captor.value.message)
        assertEquals(NOW, captor.value.sentAt)
        assertEquals(ThankCardColor.BLUE, captor.value.paperColor)
        assertEquals(ThankCardColor.BLUE, response.paperColor)
        assertEquals(THANK_CARD_ID, response.thankCardId)
        assertEquals("https://botem.me/thank-cards/${captor.value.shareKey}", response.shareUrl)
    }

    @Test
    fun `서프라이즈를 연 수신자가 주최자 보탬에도 감사카드를 보낸다`() {
        val funding = surpriseFunding()
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)
        given(contributionRepository.findByIdAndFundingIdForUpdate(CONTRIBUTION_ID, FUNDING_ID))
            .willReturn(contribution(participantUserId = OWNER_ID).withId(CONTRIBUTION_ID))
        given(thankCardRepository.findByContributionId(CONTRIBUTION_ID)).willReturn(null)
        given(thankCardRepository.save(any(ContributionThankCard::class.java))).willAnswer { invocation ->
            (invocation.arguments[0] as ContributionThankCard).withId(THANK_CARD_ID)
        }

        val response = service.send(RECIPIENT_ID, FUNDING_ID, CONTRIBUTION_ID, SendContributionThankCardRequest("고마워"))

        assertEquals(THANK_CARD_ID, response.thankCardId)
        verify(thankCardRepository).save(any(ContributionThankCard::class.java))
    }

    @Test
    fun `수신자는 취소된 보탬을 제외한 고마운 친구 목록을 조회한다`() {
        val funding = surpriseFunding()
        val organizerContribution = contribution(participantUserId = OWNER_ID).withId(CONTRIBUTION_ID)
        setBaseField(organizerContribution, "createdAt", LocalDateTime.of(2026, 11, 19, 14, 0))
        val cancelled = contribution().withId(CONTRIBUTION_ID + 1).also { it.cancel() }
        given(fundingRepository.findById(FUNDING_ID)).willReturn(Optional.of(funding))
        given(contributionRepository.findAllByFundingIdOrderByCreatedAtAscIdAsc(FUNDING_ID))
            .willReturn(listOf(organizerContribution, cancelled))
        given(rollingPaperMessageRepository.findAllByContributionIdIn(listOf(CONTRIBUTION_ID))).willReturn(
            listOf(RollingPaperMessage(CONTRIBUTION_ID, "축하해!", "LIME", photoUrl = "https://image.example.com/photo.jpg")),
        )
        given(thankCardRepository.findAllByContributionIdIn(listOf(CONTRIBUTION_ID))).willReturn(emptyList())

        val response = service.getSurpriseThankCardFriends(RECIPIENT_ID, FUNDING_ID)

        assertEquals(listOf(CONTRIBUTION_ID), response.friends.map { it.contributionId })
        assertTrue(response.friends.single().organizer)
        assertEquals(LocalDate.of(2026, 11, 19), response.friends.single().contributedDate)
        assertEquals(listOf(com.botem.domain.funding.dto.response.RollingPaperMessageType.TEXT, com.botem.domain.funding.dto.response.RollingPaperMessageType.PHOTO), response.friends.single().messageTypes)
    }

    @Test
    fun `이모지는 Unicode 코드 포인트 한 글자로 계산한다`() {
        givenSendableFundingAndContribution()
        given(thankCardRepository.findByContributionId(CONTRIBUTION_ID)).willReturn(null)
        given(thankCardRepository.save(any(ContributionThankCard::class.java))).willAnswer { invocation ->
            (invocation.arguments[0] as ContributionThankCard).withId(THANK_CARD_ID)
        }
        val message = "😀".repeat(ContributionThankCard.MAX_MESSAGE_LENGTH)

        val response = service.send(
            OWNER_ID,
            FUNDING_ID,
            CONTRIBUTION_ID,
            SendContributionThankCardRequest(message),
        )

        assertEquals(message, response.message)
    }

    @Test
    fun `같은 보탬에 다시 보내면 최초 감사카드를 그대로 반환한다`() {
        givenSendableFundingAndContribution()
        val existing = thankCard(message = "최초 메시지", sentAt = NOW.minusMinutes(1))
        given(thankCardRepository.findByContributionId(CONTRIBUTION_ID)).willReturn(existing)

        val response = service.send(
            OWNER_ID,
            FUNDING_ID,
            CONTRIBUTION_ID,
            SendContributionThankCardRequest("변경 메시지", ThankCardColor.ORANGE),
        )

        // 다시 보내도 처음 고른 색상을 바꾸지 않는다
        assertEquals("최초 메시지", response.message)
        assertEquals(ThankCardColor.LIME, response.paperColor)
        assertEquals(NOW.minusMinutes(1), response.sentAt)
        verify(thankCardRepository, never()).save(any())
    }

    @Test
    fun `펀딩이 없거나 취소됐으면 감사카드를 보낼 수 없다`() {
        listOf(null, funding(status = FundingStatus.CANCELLED)).forEach { funding ->
            given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)

            val exception = assertFailsWith<BusinessException> {
                service.send(OWNER_ID, FUNDING_ID, CONTRIBUTION_ID, SendContributionThankCardRequest("고마워"))
            }

            assertEquals(FundingErrorCode.FUNDING_NOT_FOUND, exception.errorCode)
        }
    }

    @Test
    fun `개설자가 아니면 감사카드를 보낼 수 없다`() {
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding())

        val exception = assertFailsWith<BusinessException> {
            service.send(OTHER_USER_ID, FUNDING_ID, CONTRIBUTION_ID, SendContributionThankCardRequest("고마워"))
        }

        assertEquals(FundingErrorCode.FUNDING_OWNER_ONLY, exception.errorCode)
        verify(contributionRepository, never()).findByIdAndFundingIdForUpdate(CONTRIBUTION_ID, FUNDING_ID)
    }

    @Test
    fun `보탬이 없거나 확정 상태가 아니면 감사카드를 보낼 수 없다`() {
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding())
        given(contributionRepository.findByIdAndFundingIdForUpdate(CONTRIBUTION_ID, FUNDING_ID)).willReturn(null)
        assertEquals(
            FundingErrorCode.CONTRIBUTION_NOT_FOUND,
            assertFailsWith<BusinessException> {
                service.send(OWNER_ID, FUNDING_ID, CONTRIBUTION_ID, SendContributionThankCardRequest("고마워"))
            }.errorCode,
        )

        ContributionStatus.entries.filter { it != ContributionStatus.CONFIRMED }.forEach { status ->
            given(contributionRepository.findByIdAndFundingIdForUpdate(CONTRIBUTION_ID, FUNDING_ID))
                .willReturn(contribution(status))

            val exception = assertFailsWith<BusinessException> {
                service.send(OWNER_ID, FUNDING_ID, CONTRIBUTION_ID, SendContributionThankCardRequest("고마워"))
            }

            assertEquals(FundingErrorCode.CONTRIBUTION_THANK_CARD_CONFIRMED_ONLY, exception.errorCode, status.name)
        }
    }

    @Test
    fun `감사 메시지가 공백이거나 50자를 넘으면 거부한다`() {
        givenSendableFundingAndContribution()
        given(thankCardRepository.findByContributionId(CONTRIBUTION_ID)).willReturn(null)

        listOf(" ", "😀".repeat(ContributionThankCard.MAX_MESSAGE_LENGTH + 1)).forEach { message ->
            val exception = assertFailsWith<BusinessException> {
                service.send(OWNER_ID, FUNDING_ID, CONTRIBUTION_ID, SendContributionThankCardRequest(message))
            }

            assertEquals(CommonErrorCode.INVALID_INPUT_VALUE, exception.errorCode)
        }
        verify(thankCardRepository, never()).save(any())
    }

    @Test
    fun `로그인 참여자는 본인이 받은 감사카드를 조회한다`() {
        val contribution = contribution().withId(CONTRIBUTION_ID)
        val card = thankCard()
        given(contributionRepository.findByIdAndParticipantUserId(CONTRIBUTION_ID, PARTICIPANT_ID))
            .willReturn(contribution)
        given(thankCardRepository.findByContributionId(CONTRIBUTION_ID)).willReturn(card)
        givenDetail(card)

        val response = service.getForParticipant(PARTICIPANT_ID, CONTRIBUTION_ID)

        assertEquals("지유", response.senderName)
        assertEquals("하린", response.recipientName)
        assertEquals("무선 헤드폰", response.productName)
        assertEquals("https://image.example.com/product.jpg", response.productImageUrl)
        assertEquals("https://botem.me/thank-cards/$SHARE_KEY", response.shareUrl)
        assertEquals(ContributionThankCardSenderRole.FUNDING_OWNER, response.senderRole)
        assertFalse(response.toString().contains("10000"))
    }

    @Test
    fun `다른 회원의 보탬이거나 감사카드가 없으면 조회할 수 없다`() {
        given(contributionRepository.findByIdAndParticipantUserId(CONTRIBUTION_ID, OTHER_USER_ID)).willReturn(null)
        assertEquals(
            FundingErrorCode.CONTRIBUTION_THANK_CARD_NOT_FOUND,
            assertFailsWith<BusinessException> {
                service.getForParticipant(OTHER_USER_ID, CONTRIBUTION_ID)
            }.errorCode,
        )

        val contribution = contribution().withId(CONTRIBUTION_ID)
        given(contributionRepository.findByIdAndParticipantUserId(CONTRIBUTION_ID, PARTICIPANT_ID))
            .willReturn(contribution)
        given(thankCardRepository.findByContributionId(CONTRIBUTION_ID)).willReturn(null)
        assertEquals(
            FundingErrorCode.CONTRIBUTION_THANK_CARD_NOT_FOUND,
            assertFailsWith<BusinessException> {
                service.getForParticipant(PARTICIPANT_ID, CONTRIBUTION_ID)
            }.errorCode,
        )
    }

    @Test
    fun `공유 키로 로그인 없이 감사카드를 조회한다`() {
        val contribution = contribution().withId(CONTRIBUTION_ID)
        val card = thankCard()
        given(thankCardRepository.findByShareKey(SHARE_KEY)).willReturn(card)
        given(contributionRepository.findById(CONTRIBUTION_ID)).willReturn(Optional.of(contribution))
        givenDetail(card)

        val response = service.getByShareKey(SHARE_KEY)

        assertEquals(THANK_CARD_ID, response.thankCardId)
        assertEquals("고마워", response.message)
        assertEquals(ThankCardColor.LIME, response.paperColor)
    }

    @Test
    fun `서프라이즈 수신자가 보낸 감사카드에는 발신 역할과 펀딩 유형을 반환한다`() {
        val contribution = contribution().withId(CONTRIBUTION_ID)
        val card = thankCard(senderUserId = RECIPIENT_ID)
        given(thankCardRepository.findByShareKey(SHARE_KEY)).willReturn(card)
        given(contributionRepository.findById(CONTRIBUTION_ID)).willReturn(Optional.of(contribution))
        given(fundingRepository.findById(FUNDING_ID)).willReturn(Optional.of(surpriseFunding()))
        given(userRepository.findById(RECIPIENT_ID)).willReturn(Optional.of(user().withId(RECIPIENT_ID)))

        val response = service.getByShareKey(SHARE_KEY)

        assertEquals(ContributionThankCardSenderRole.SURPRISE_RECIPIENT, response.senderRole)
        assertEquals(FundingType.SURPRISE, response.fundingType)
    }

    @Test
    fun `존재하지 않는 공유 키는 조회할 수 없다`() {
        given(thankCardRepository.findByShareKey(SHARE_KEY)).willReturn(null)

        val exception = assertFailsWith<BusinessException> { service.getByShareKey(SHARE_KEY) }

        assertEquals(FundingErrorCode.CONTRIBUTION_THANK_CARD_NOT_FOUND, exception.errorCode)
    }

    private fun givenSendableFundingAndContribution() {
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding())
        given(contributionRepository.findByIdAndFundingIdForUpdate(CONTRIBUTION_ID, FUNDING_ID))
            .willReturn(contribution())
    }

    private fun givenDetail(card: ContributionThankCard) {
        given(fundingRepository.findById(FUNDING_ID)).willReturn(Optional.of(funding()))
        given(userRepository.findById(card.senderUserId)).willReturn(Optional.of(user()))
    }

    private fun funding(status: FundingStatus = FundingStatus.FUNDING): Funding =
        Funding(
            ownerId = OWNER_ID,
            recipientId = null,
            type = FundingType.BIRTHDAY,
            title = "지유의 생일",
            targetAmount = 100_000L,
            deadline = NOW.plusDays(1),
            revealAt = null,
            story = null,
            promise = null,
            product = FundingProductSnapshot(
                sourceWishId = 1L,
                name = "무선 헤드폰",
                imageUrl = "https://image.example.com/product.jpg",
                price = 100_000L,
                url = "https://shop.example.com/product",
            ),
            payoutAccount = FundingPayoutAccountSnapshot(null, Bank.TOSS_BANK, "encrypted", "지유"),
        ).withId(FUNDING_ID).also { funding ->
            if (status == FundingStatus.CANCELLED) {
                setField(funding, Funding::class.java, "status", status)
            }
        }

    private fun surpriseFunding(): Funding =
        Funding(
            ownerId = OWNER_ID,
            recipientId = RECIPIENT_ID,
            type = FundingType.SURPRISE,
            title = "지유를 위한 서프라이즈",
            targetAmount = 100_000L,
            deadline = NOW.minusDays(1),
            revealAt = NOW.minusDays(1),
            story = null,
            promise = null,
            product = FundingProductSnapshot(
                sourceWishId = 1L,
                name = "무선 헤드폰",
                imageUrl = "https://image.example.com/product.jpg",
                price = 100_000L,
                url = "https://shop.example.com/product",
            ),
            payoutAccount = FundingPayoutAccountSnapshot(null, Bank.TOSS_BANK, "encrypted", "지유"),
        ).withId(FUNDING_ID).also { funding ->
            setField(funding, Funding::class.java, "status", FundingStatus.REVEALED)
            setField(funding, Funding::class.java, "surpriseOpenedAt", NOW.minusMinutes(1))
        }

    private fun contribution(
        status: ContributionStatus = ContributionStatus.CONFIRMED,
        participantUserId: Long? = PARTICIPANT_ID,
    ): Contribution =
        Contribution(
            fundingId = FUNDING_ID,
            participantUserId = participantUserId,
            participantName = "하린",
            amount = 10_000L,
            status = ContributionStatus.AWAITING_TRANSFER,
            guestPasswordHash = null,
        ).also { contribution ->
            when (status) {
                ContributionStatus.AWAITING_TRANSFER -> Unit
                ContributionStatus.PENDING_CONFIRMATION -> contribution.markTransferSent()
                ContributionStatus.CONFIRMED -> {
                    contribution.markTransferSent()
                    contribution.confirm(NOW.minusHours(1))
                }
                else -> setField(contribution, Contribution::class.java, "status", status)
            }
        }

    private fun thankCard(
        message: String = "고마워",
        sentAt: LocalDateTime = NOW,
        senderUserId: Long = OWNER_ID,
    ): ContributionThankCard =
        ContributionThankCard(CONTRIBUTION_ID, senderUserId, message, SHARE_KEY, sentAt).withId(THANK_CARD_ID)

    private fun user(): User =
        User(kakaoId = 1L, nickname = "지유닉네임", profileImageUrl = null, name = "지유").withId(OWNER_ID)

    private fun <T : BaseEntity> T.withId(id: Long): T = apply {
        setField(this, this::class.java, "id", id)
    }

    private fun setField(target: Any, owner: Class<*>, name: String, value: Any) {
        owner.getDeclaredField(name).apply {
            isAccessible = true
            set(target, value)
        }
    }

    private fun setBaseField(target: Any, name: String, value: Any) {
        target.javaClass.superclass.getDeclaredField(name).apply { isAccessible = true; set(target, value) }
    }

    companion object {
        private const val OWNER_ID = 1L
        private const val PARTICIPANT_ID = 2L
        private const val OTHER_USER_ID = 3L
        private const val RECIPIENT_ID = 4L
        private const val FUNDING_ID = 10L
        private const val CONTRIBUTION_ID = 20L
        private const val THANK_CARD_ID = 30L
        private const val SHARE_KEY = "550e8400e29b41d4a716446655440000"
        private val NOW = LocalDateTime.of(2026, 11, 19, 20, 0)
    }
}
