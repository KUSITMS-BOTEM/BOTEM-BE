package com.botem.domain.funding.service

import com.botem.domain.account.entity.Bank
import com.botem.domain.funding.dto.response.ClosedFundingGiftVerificationStatus
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.projection.FundingContributionSummary
import com.botem.domain.user.entity.User
import com.botem.domain.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClosedFundingServiceTest {

    private val fundingRepository = mock(FundingRepository::class.java)
    private val contributionRepository = mock(ContributionRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val service = ClosedFundingService(
        fundingRepository = fundingRepository,
        contributionRepository = contributionRepository,
        userRepository = userRepository,
        clock = Clock.fixed(Instant.parse("2026-11-15T00:41:00Z"), ZoneId.of("Asia/Seoul")),
    )

    @Test
    fun `생일 펀딩은 팡 이후 인증 상태를, 서프라이즈는 받는 사람 이름을 반환한다`() {
        val unopened = birthday(id = 1L)
        val verificationRequired = birthday(id = 2L).also { it.openResult(NOW) }
        val verified = birthday(id = 3L).also { setField(it, "status", FundingStatus.COMPLETED) }
        val surprise = surprise(id = 4L)
        given(fundingRepository.findClosedFundingsByOwnerId(USER_ID, NOW))
            .willReturn(listOf(unopened, verificationRequired, verified, surprise))
        given(contributionRepository.summarizeByFundingIds(listOf(1L, 2L, 3L, 4L)))
            .willReturn(
                listOf(
                    FundingContributionSummary(1L, 100_000L, 0L, 0L, 8L),
                    FundingContributionSummary(4L, 99_999L, 50_000L, 1L, 6L),
                ),
            )
        given(userRepository.findAllById(listOf(RECIPIENT_ID))).willReturn(listOf(recipient()))

        val fundings = service.getClosedFundings(USER_ID).fundings

        assertNull(fundings[0].giftVerificationStatus)
        assertEquals(ClosedFundingGiftVerificationStatus.REQUIRED, fundings[1].giftVerificationStatus)
        assertEquals(ClosedFundingGiftVerificationStatus.COMPLETED, fundings[2].giftVerificationStatus)
        assertNull(fundings[3].giftVerificationStatus)
        assertNull(fundings[0].recipientName)
        assertEquals("서준", fundings[3].recipientName)
        assertTrue(fundings[0].targetReached)
        assertEquals(8L, fundings[0].participantCount)
        assertFalse(fundings[3].targetReached, "확인 대기 금액은 목표 달성에 반영하지 않는다")
        assertFalse(fundings[1].targetReached, "참여가 없으면 목표 미달이다")
        assertEquals(0L, fundings[1].participantCount)
    }

    @Test
    fun `마감된 펀딩이 없으면 추가 조회 없이 빈 목록을 반환한다`() {
        given(fundingRepository.findClosedFundingsByOwnerId(USER_ID, NOW)).willReturn(emptyList())

        assertEquals(emptyList(), service.getClosedFundings(USER_ID).fundings)
        verifyNoInteractions(contributionRepository, userRepository)
    }

    private fun birthday(id: Long): Funding =
        funding(id, null, FundingType.BIRTHDAY, null)

    private fun surprise(id: Long): Funding =
        funding(id, RECIPIENT_ID, FundingType.SURPRISE, NOW.minusHours(1))
            .also { setField(it, "status", FundingStatus.REVEALED) }

    private fun funding(id: Long, recipientId: Long?, type: FundingType, revealAt: LocalDateTime?): Funding =
        Funding(
            ownerId = USER_ID,
            recipientId = recipientId,
            type = type,
            title = null,
            targetAmount = 100_000L,
            deadline = NOW.minusDays(1),
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
                bank = Bank.KAKAO_BANK,
                encryptedAccountNumber = "encrypted",
                accountHolder = null,
            ),
        ).also { setField(it, "id", id) }

    private fun recipient(): User =
        User(
            kakaoId = 1234L,
            nickname = "서준닉네임",
            profileImageUrl = null,
            name = "서준",
        ).also { setField(it, "id", RECIPIENT_ID) }

    private fun setField(target: Any, fieldName: String, value: Any) {
        target.javaClass.getDeclaredField(fieldName).apply {
            isAccessible = true
            set(target, value)
        }
    }

    companion object {
        private const val USER_ID = 1L
        private const val RECIPIENT_ID = 5L
        private val NOW = LocalDateTime.of(2026, 11, 15, 9, 41)
    }
}
