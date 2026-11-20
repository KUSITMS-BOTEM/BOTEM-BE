package com.botem.domain.funding.service

import com.botem.domain.account.entity.Bank
import com.botem.domain.funding.dto.response.FundingDetailGiftVerificationResponse
import com.botem.domain.funding.dto.response.FundingDetailThankCardResponse
import com.botem.domain.funding.dto.response.FundingDisplayStatus
import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.entity.ContributionStatus
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
import com.botem.domain.funding.repository.SurpriseThankCardRepository
import com.botem.domain.user.entity.User
import com.botem.domain.user.repository.UserRepository
import com.botem.global.exception.BusinessException
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyIterable
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
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

class FundingDetailServiceTest {

    private val fundingRepository = mock(FundingRepository::class.java)
    private val contributionRepository = mock(ContributionRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val giftVerificationRepository = mock(GiftVerificationRepository::class.java)
    private val surpriseThankCardRepository = mock(SurpriseThankCardRepository::class.java)
    private val service = FundingDetailService(
        fundingRepository = fundingRepository,
        contributionRepository = contributionRepository,
        userRepository = userRepository,
        giftVerificationRepository = giftVerificationRepository,
        surpriseThankCardRepository = surpriseThankCardRepository,
        clock = Clock.fixed(Instant.parse("2026-11-14T15:00:00Z"), ZoneId.of("Asia/Seoul")),
    )

    @Test
    fun `로그인 참여자는 한 번만, 비로그인 참여는 각각 집계하고 취소된 참여는 제외한다`() {
        givenFunding(funding())
        givenContributions(
            contribution(userId = PARTICIPANT_ID, name = "지유", amount = 30_000L, confirmed = true),
            contribution(userId = null, name = "익명1", amount = 10_000L, status = ContributionStatus.PENDING_CONFIRMATION),
            contribution(userId = PARTICIPANT_ID, name = "지유 다른 이름", amount = 20_000L, confirmed = true),
            contribution(userId = null, name = "익명2", amount = 0L, status = ContributionStatus.MESSAGE_ONLY),
            contribution(userId = 99L, name = "취소한 친구", amount = 50_000L, status = ContributionStatus.CANCELLED),
        )
        given(userRepository.findAllById(setOf(PARTICIPANT_ID)))
            .willReturn(listOf(user(PARTICIPANT_ID, "https://image.example.com/jiyu.jpg")))

        val result = service.getDetail(null, FUNDING_ID)

        assertEquals(3, result.participantCount)
        assertEquals(listOf(1, 2, 3), result.participants.map { it.order })
        assertEquals(listOf("지유", "익명1", "익명2"), result.participants.map { it.name })
        assertEquals("https://image.example.com/jiyu.jpg", result.participants[0].profileImageUrl)
        assertNull(result.participants[1].profileImageUrl)
        assertEquals(50, result.confirmedPercentage)
        assertEquals(10, result.pendingPercentage)
    }

    @Test
    fun `마감 전이고 확정 금액이 목표에 못 미치면 진행 중이며 금액을 보탤 수 있다`() {
        givenFunding(funding(deadline = NOW.plusDays(3)))
        givenContributions(contribution(amount = 100_000L, status = ContributionStatus.PENDING_CONFIRMATION))

        val result = service.getDetail(null, FUNDING_ID)

        assertEquals(FundingDisplayStatus.IN_PROGRESS, result.displayStatus)
        assertTrue(result.canContributeAmount)
        assertTrue(result.canParticipate)
        assertEquals(3L, result.remainingDays)
    }

    @Test
    fun `확정 금액이 목표에 도달하면 금액 보태기만 막고 참여는 허용한다`() {
        givenFunding(funding(deadline = NOW.plusDays(3)))
        givenContributions(contribution(amount = 100_000L, confirmed = true))

        val result = service.getDetail(null, FUNDING_ID)

        assertEquals(FundingDisplayStatus.TARGET_REACHED, result.displayStatus)
        assertFalse(result.canContributeAmount)
        assertTrue(result.canParticipate)
    }

    @Test
    fun `마감일이 지나면 마감 상태이며 참여할 수 없다`() {
        givenFunding(funding(deadline = NOW.minusSeconds(1)))
        givenContributions()

        val result = service.getDetail(null, FUNDING_ID)

        assertEquals(FundingDisplayStatus.CLOSED, result.displayStatus)
        assertFalse(result.canContributeAmount)
        assertFalse(result.canParticipate)
        assertNull(result.remainingDays)
    }

    @Test
    fun `조기 마감한 일반 펀딩은 금액을 막고 기존 마감일까지 메시지는 허용한다`() {
        givenFunding(funding(earlyClosedAt = NOW.minusMinutes(1)))
        givenContributions()

        val result = service.getDetail(null, FUNDING_ID)

        assertEquals(FundingDisplayStatus.CLOSED, result.displayStatus)
        assertFalse(result.canContributeAmount)
        assertTrue(result.canParticipate)
    }

    @Test
    fun `상품 변경 여부와 마지막 변경 시각을 반환한다`() {
        givenFunding(funding(productChangedAt = NOW.minusHours(1)))
        givenContributions()

        val result = service.getDetail(null, FUNDING_ID)

        assertTrue(result.productChanged)
        assertEquals(NOW.minusHours(1), result.productChangedAt)
    }

    @Test
    fun `결과를 연 뒤에는 선물 준비 중, 인증을 마치면 인증 완료 상태다`() {
        givenContributions()

        givenFunding(
            funding(
                deadline = NOW.minusDays(1),
                status = FundingStatus.GIFT_PREPARING,
                earlyClosedAt = NOW.minusDays(2),
            ),
        )
        assertEquals(FundingDisplayStatus.GIFT_PREPARING, service.getDetail(null, FUNDING_ID).displayStatus)

        givenFunding(funding(deadline = NOW.minusDays(1), status = FundingStatus.COMPLETED))
        assertEquals(FundingDisplayStatus.COMPLETED, service.getDetail(null, FUNDING_ID).displayStatus)
    }

    @Test
    fun `개설자가 조회하면 미리보기로 표시한다`() {
        givenFunding(funding())
        givenContributions()

        assertTrue(service.getDetail(OWNER_ID, FUNDING_ID).ownerPreview)
        assertFalse(service.getDetail(PARTICIPANT_ID, FUNDING_ID).ownerPreview)
        assertFalse(service.getDetail(null, FUNDING_ID).ownerPreview)
    }

    @Test
    fun `생일은 개설자, 서프라이즈는 받는 사람을 주인공으로 하고 목표 금액과 연장 여부를 반환한다`() {
        given(userRepository.findById(OWNER_ID)).willReturn(
            Optional.of(User(kakaoId = 1L, nickname = "지유닉", profileImageUrl = "https://image.example.com/jiyu.jpg", name = "지유")),
        )
        given(userRepository.findById(PARTICIPANT_ID)).willReturn(
            Optional.of(User(kakaoId = 2L, nickname = "서준닉", profileImageUrl = null)),
        )
        givenContributions()

        givenFunding(funding())
        val birthday = service.getDetail(null, FUNDING_ID)
        assertEquals("지유", birthday.celebrantName)
        assertEquals("https://image.example.com/jiyu.jpg", birthday.celebrantProfileImageUrl)
        assertEquals(100_000L, birthday.targetAmount)
        assertFalse(birthday.extended)

        givenFunding(funding(type = FundingType.SURPRISE))
        // 이름이 없으면 닉네임을 쓴다
        assertEquals("서준닉", service.getDetail(OWNER_ID, FUNDING_ID).celebrantName)

        givenFunding(funding().also { setField(it, "extensionCount", 1) })
        assertTrue(service.getDetail(null, FUNDING_ID).extended)
    }

    @Test
    fun `취소된 펀딩은 찾을 수 없다`() {
        givenFunding(funding(status = FundingStatus.CANCELLED))

        val exception = assertFailsWith<BusinessException> { service.getDetail(null, FUNDING_ID) }

        assertEquals(FundingErrorCode.FUNDING_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `존재하지 않는 펀딩은 찾을 수 없다`() {
        given(fundingRepository.findById(FUNDING_ID)).willReturn(Optional.empty())

        val exception = assertFailsWith<BusinessException> { service.getDetail(null, FUNDING_ID) }

        assertEquals(FundingErrorCode.FUNDING_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `선물 인증을 마친 펀딩은 인증 사진과 감사 메시지를 반환한다`() {
        givenFunding(funding(deadline = NOW.minusDays(1), status = FundingStatus.COMPLETED))
        givenContributions()
        val giftVerification = GiftVerification(fundingId = FUNDING_ID).also {
            it.complete(
                imageUrl = "https://image.example.com/gift.jpg",
                thankYouMessage = "고마워!",
                purchasedThroughBotemLink = true,
                completedAt = NOW,
                replacementProductName = "다른 헤드폰",
            )
        }
        given(giftVerificationRepository.findByFundingId(FUNDING_ID)).willReturn(giftVerification)

        val result = service.getDetail(null, FUNDING_ID)

        assertEquals(
            FundingDetailGiftVerificationResponse(
                imageUrl = "https://image.example.com/gift.jpg",
                thankYouMessage = "고마워!",
                completedAt = NOW,
            ),
            result.giftVerification,
        )
    }

    @Test
    fun `인증 전 펀딩은 선물 인증을 조회하지 않고 null을 반환한다`() {
        givenFunding(funding(deadline = NOW.minusDays(1), status = FundingStatus.GIFT_PREPARING))
        givenContributions()

        val result = service.getDetail(null, FUNDING_ID)

        assertNull(result.giftVerification)
        verifyNoInteractions(giftVerificationRepository)
    }

    @Test
    fun `서프라이즈 받는 사람이 보낸 감사 카드를 반환하고 생일 펀딩은 조회하지 않는다`() {
        givenContributions()
        given(surpriseThankCardRepository.findByFundingId(FUNDING_ID)).willReturn(
            SurpriseThankCard(fundingId = FUNDING_ID, senderUserId = PARTICIPANT_ID, message = "다들 고마워", sentAt = NOW),
        )

        givenFunding(funding(type = FundingType.SURPRISE, deadline = NOW.minusDays(1), status = FundingStatus.REVEALED))
        assertEquals(
            FundingDetailThankCardResponse(message = "다들 고마워", sentAt = NOW),
            service.getDetail(null, FUNDING_ID).thankCard,
        )

        givenFunding(funding())
        assertNull(service.getDetail(null, FUNDING_ID).thankCard)
        verify(surpriseThankCardRepository, times(1)).findByFundingId(FUNDING_ID)
    }

    private fun givenFunding(funding: Funding) {
        given(fundingRepository.findById(FUNDING_ID)).willReturn(Optional.of(funding))
    }

    private fun givenContributions(vararg contributions: Contribution) {
        given(contributionRepository.findAllByFundingIdOrderByCreatedAtAscIdAsc(FUNDING_ID))
            .willReturn(contributions.toList())
        given(userRepository.findAllById(anyIterable())).willReturn(emptyList())
    }

    @Test
    fun `서프라이즈를 받는 사람이 공개 전에 조회하면 정보를 내려주지 않는다`() {
        givenFunding(funding(type = FundingType.SURPRISE))

        val exception = assertFailsWith<BusinessException> {
            service.getDetail(PARTICIPANT_ID, FUNDING_ID)
        }

        assertEquals(FundingErrorCode.SURPRISE_NOT_REVEALED, exception.errorCode)
        verifyNoInteractions(contributionRepository)
    }

    @Test
    fun `서프라이즈를 받는 사람은 선물 준비가 끝났으면 공개 시각부터 조회할 수 있다`() {
        givenFunding(
            funding(deadline = NOW.minusDays(1), status = FundingStatus.WAITING_REVEAL, type = FundingType.SURPRISE),
        )
        givenContributions()

        val result = service.getDetail(PARTICIPANT_ID, FUNDING_ID)

        assertEquals(FundingType.SURPRISE, result.type)
    }

    @Test
    fun `선물 준비 전인 서프라이즈는 공개 시각이 지나도 받는 사람에게 내려주지 않는다`() {
        givenFunding(funding(deadline = NOW.minusDays(1), status = FundingStatus.CLOSED, type = FundingType.SURPRISE))

        val exception = assertFailsWith<BusinessException> {
            service.getDetail(PARTICIPANT_ID, FUNDING_ID)
        }

        assertEquals(FundingErrorCode.SURPRISE_NOT_REVEALED, exception.errorCode)
        verifyNoInteractions(contributionRepository)
    }

    @Test
    fun `서프라이즈를 받는 사람이 아니면 공개 전에도 조회할 수 있다`() {
        givenFunding(funding(type = FundingType.SURPRISE))
        givenContributions()

        val result = service.getDetail(OWNER_ID, FUNDING_ID)

        assertEquals(FundingType.SURPRISE, result.type)
    }

    private fun funding(
        deadline: LocalDateTime = NOW.plusDays(3),
        status: FundingStatus = FundingStatus.FUNDING,
        type: FundingType = FundingType.BIRTHDAY,
        earlyClosedAt: LocalDateTime? = null,
        productChangedAt: LocalDateTime? = null,
    ): Funding =
        Funding(
            ownerId = OWNER_ID,
            recipientId = if (type == FundingType.SURPRISE) PARTICIPANT_ID else null,
            type = type,
            title = "생일 선물 펀딩",
            targetAmount = 100_000L,
            deadline = deadline,
            revealAt = if (type == FundingType.SURPRISE) deadline.plusDays(1) else null,
            story = "새 헤드폰이 필요해요",
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
            earlyClosedAt?.let { setField(funding, "earlyClosedAt", it) }
            productChangedAt?.let { setField(funding, "productChangedAt", it) }
        }

    private fun contribution(
        userId: Long? = null,
        name: String = "친구",
        amount: Long,
        status: ContributionStatus = ContributionStatus.AWAITING_TRANSFER,
        confirmed: Boolean = false,
    ): Contribution =
        Contribution(
            fundingId = FUNDING_ID,
            participantUserId = userId,
            participantName = name,
            amount = amount,
            status = status,
            guestPasswordHash = null,
        ).also {
            if (confirmed) {
                it.markTransferSent()
                it.confirm(NOW)
            }
        }

    private fun user(id: Long, profileImageUrl: String?): User =
        User(kakaoId = id, nickname = "친구", profileImageUrl = profileImageUrl)
            .also { setField(it, "id", id) }

    private fun setField(target: Any, fieldName: String, value: Any) {
        target.javaClass.getDeclaredField(fieldName).apply {
            isAccessible = true
            set(target, value)
        }
    }

    companion object {
        private const val FUNDING_ID = 12L
        private const val OWNER_ID = 1L
        private const val PARTICIPANT_ID = 10L
        private val NOW = LocalDateTime.of(2026, 11, 15, 0, 0)
    }
}
