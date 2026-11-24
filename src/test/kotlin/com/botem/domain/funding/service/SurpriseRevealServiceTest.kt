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
import com.botem.domain.funding.repository.projection.RollingPaperMessageProjection
import com.botem.domain.user.entity.User
import com.botem.domain.user.exception.UserErrorCode
import com.botem.domain.user.repository.UserRepository
import com.botem.global.exception.BusinessException
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
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
    private val contributionRepository = mock(ContributionRepository::class.java)
    private val rollingPaperMessageRepository = mock(RollingPaperMessageRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val service = SurpriseRevealService(
        fundingRepository,
        contributionRepository,
        rollingPaperMessageRepository,
        userRepository,
        Clock.fixed(Instant.parse("2026-11-15T00:41:00Z"), ZoneId.of("Asia/Seoul")),
    )

    @Test
    fun `수신자가 공개된 서프라이즈 상세을 조회한다`() {
        given(fundingRepository.findById(FUNDING_ID)).willReturn(Optional.of(funding()))
        given(userRepository.findById(RECIPIENT_ID)).willReturn(Optional.of(user(RECIPIENT_ID, "지유")))
        given(userRepository.findById(OWNER_ID)).willReturn(Optional.of(user(OWNER_ID, "민지")))
        given(contributionRepository.summarizeByFundingIds(listOf(FUNDING_ID))).willReturn(emptyList())
        given(rollingPaperMessageRepository.findAllByFundingId(FUNDING_ID)).willReturn(emptyList())

        val result = service.getDetail(RECIPIENT_ID, FUNDING_ID)

        assertEquals("지유", result.recipientName)
        assertEquals("민지", result.organizerName)
    }

    @Test
    fun `존재하지 않는 서프라이즈는 조회하거나 열 수 없다`() {
        given(fundingRepository.findById(FUNDING_ID)).willReturn(Optional.empty())
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(null)

        val detail = assertFailsWith<BusinessException> { service.getDetail(RECIPIENT_ID, FUNDING_ID) }
        val open = assertFailsWith<BusinessException> { service.open(RECIPIENT_ID, FUNDING_ID) }

        assertEquals(FundingErrorCode.FUNDING_NOT_FOUND, detail.errorCode)
        assertEquals(FundingErrorCode.FUNDING_NOT_FOUND, open.errorCode)
    }

    @Test
    fun `공개 전 서프라이즈는 수신자가 아닌 사용자에게도 존재 여부를 노출하지 않는다`() {
        given(fundingRepository.findById(FUNDING_ID)).willReturn(Optional.of(funding(revealAt = NOW.plusSeconds(1))))

        val exception = assertFailsWith<BusinessException> {
            service.getDetail(RECIPIENT_ID + 1, FUNDING_ID)
        }

        assertEquals(FundingErrorCode.SURPRISE_REVEAL_NOT_AVAILABLE, exception.errorCode)
    }

    @Test
    fun `공개된 서프라이즈를 수신자가 아니면 조회할 수 없다`() {
        given(fundingRepository.findById(FUNDING_ID)).willReturn(Optional.of(funding()))

        val exception = assertFailsWith<BusinessException> {
            service.getDetail(RECIPIENT_ID + 1, FUNDING_ID)
        }

        assertEquals(FundingErrorCode.SURPRISE_RECIPIENT_ONLY, exception.errorCode)
    }

    @Test
    fun `참여 요약과 메시지가 없으면 빈 상세와 닉네임을 반환한다`() {
        given(fundingRepository.findById(FUNDING_ID)).willReturn(Optional.of(funding()))
        given(userRepository.findById(RECIPIENT_ID)).willReturn(Optional.of(user(RECIPIENT_ID, "수신자 닉네임")))
        given(userRepository.findById(OWNER_ID)).willReturn(Optional.of(user(OWNER_ID, "주최자 닉네임")))
        given(contributionRepository.summarizeByFundingIds(listOf(FUNDING_ID))).willReturn(emptyList())
        given(rollingPaperMessageRepository.findAllByFundingId(FUNDING_ID)).willReturn(emptyList())

        val result = service.getDetail(RECIPIENT_ID, FUNDING_ID)

        assertEquals("수신자 닉네임", result.recipientName)
        assertEquals("주최자 닉네임", result.organizerName)
        assertEquals(0L, result.confirmedAmount)
        assertEquals(0, result.confirmedPercentage)
        assertEquals(0L, result.participantCount)
        assertEquals(0, result.messageCount)
        assertNull(result.openedAt)
    }

    @Test
    fun `목표 금액과 마감일, 확정 금액 기준 달성률을 반환한다`() {
        given(fundingRepository.findById(FUNDING_ID)).willReturn(Optional.of(funding()))
        givenDetailData()

        val result = service.getDetail(RECIPIENT_ID, FUNDING_ID)

        assertEquals(100_000L, result.targetAmount)
        assertEquals(NOW.minusDays(1), result.deadline)
        // 목표를 넘겨 모은 금액도 그대로 반영하고 소수점은 버린다 (429,000 / 100,000)
        assertEquals(429, result.confirmedPercentage)
    }

    @Test
    fun `수신자에게 상품 구매 정보와 선물 선택 상태를 반환한다`() {
        val funding = funding().also {
            setField(it, "status", FundingStatus.COMPLETED)
            setField(it, "productChangedAt", NOW.minusMinutes(1))
        }
        given(fundingRepository.findById(FUNDING_ID)).willReturn(Optional.of(funding))
        givenDetailData()

        val result = service.getDetail(RECIPIENT_ID, FUNDING_ID)

        assertEquals("https://shop.example.com/product", result.productUrl)
        assertEquals(100_000L, result.productPrice)
        assertTrue(result.productChanged)
        assertTrue(result.giftChosen)
    }

    @Test
    fun `수신자 또는 주최자 정보가 없으면 상세 조회를 중단한다`() {
        given(fundingRepository.findById(FUNDING_ID)).willReturn(Optional.of(funding()))
        given(userRepository.findById(RECIPIENT_ID)).willReturn(Optional.empty())

        val recipientException = assertFailsWith<BusinessException> {
            service.getDetail(RECIPIENT_ID, FUNDING_ID)
        }
        assertEquals(UserErrorCode.USER_NOT_FOUND, recipientException.errorCode)

        given(userRepository.findById(RECIPIENT_ID)).willReturn(Optional.of(user(RECIPIENT_ID, "지유")))
        given(userRepository.findById(OWNER_ID)).willReturn(Optional.empty())
        val organizerException = assertFailsWith<BusinessException> {
            service.getDetail(RECIPIENT_ID, FUNDING_ID)
        }
        assertEquals(UserErrorCode.USER_NOT_FOUND, organizerException.errorCode)
    }

    @Test
    fun `수신자가 서프라이즈를 열면 최초 열람 시각과 상태를 변경한다`() {
        val funding = funding()
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)
        givenDetailData()

        val result = service.open(RECIPIENT_ID, FUNDING_ID)

        assertEquals(NOW, result.openedAt)
        assertEquals(FundingStatus.REVEALED, funding.status)

        val retried = service.open(RECIPIENT_ID, FUNDING_ID)
        assertEquals(NOW, retried.openedAt)
        assertEquals(FundingStatus.REVEALED, funding.status)
    }

    private fun givenDetailData() {
        given(userRepository.findById(RECIPIENT_ID)).willReturn(Optional.of(user(RECIPIENT_ID, "지유")))
        given(userRepository.findById(OWNER_ID)).willReturn(Optional.of(user(OWNER_ID, "민지")))
        given(contributionRepository.summarizeByFundingIds(listOf(FUNDING_ID))).willReturn(
            listOf(FundingContributionSummary(FUNDING_ID, 429_000L, 0L, 0L, 8L)),
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
    }

    private fun funding(revealAt: LocalDateTime = NOW.minusMinutes(1)): Funding = Funding(
        ownerId = OWNER_ID, recipientId = RECIPIENT_ID, type = FundingType.SURPRISE, title = null,
        targetAmount = 100_000, deadline = NOW.minusDays(1), revealAt = revealAt, story = null, promise = null,
        product = FundingProductSnapshot(null, "헤드폰", "https://image.example.com/product.jpg", 100_000, "https://shop.example.com/product"),
        payoutAccount = FundingPayoutAccountSnapshot(null, Bank.TOSS_BANK, "encrypted", "민지"),
    ).also { setField(it, "id", FUNDING_ID); setField(it, "status", FundingStatus.WAITING_REVEAL) }

    private fun user(id: Long, name: String): User = User(kakaoId = id, nickname = name, profileImageUrl = null, name = name)
        .also { setField(it, "id", id) }

    private fun setField(target: Any, name: String, value: Any) {
        target.javaClass.getDeclaredField(name).apply { isAccessible = true; set(target, value) }
    }

    companion object {
        private const val FUNDING_ID = 1L
        private const val OWNER_ID = 2L
        private const val RECIPIENT_ID = 3L
        private val NOW = LocalDateTime.of(2026, 11, 15, 9, 41)
    }
}
