package com.botem.domain.funding.service

import com.botem.domain.account.entity.Bank
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.RollingPaperMessageRepository
import com.botem.domain.funding.repository.projection.RollingPaperMessageProjection
import com.botem.domain.user.entity.User
import com.botem.domain.user.repository.UserRepository
import com.botem.global.exception.BusinessException
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RollingPaperServiceTest {

    private val fundingRepository = mock(FundingRepository::class.java)
    private val rollingPaperMessageRepository = mock(RollingPaperMessageRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val service = RollingPaperService(fundingRepository, rollingPaperMessageRepository, userRepository)

    @Test
    fun `개설자가 결과를 연 뒤 롤링페이퍼 메시지를 조회한다`() {
        givenFunding(funding().also { it.openResult(NOW) })
        given(userRepository.findById(OWNER_ID)).willReturn(
            Optional.of(User(kakaoId = 1L, nickname = "지유닉", profileImageUrl = null, name = "지유")),
        )
        given(rollingPaperMessageRepository.findAllByFundingId(FUNDING_ID)).willReturn(
            listOf(
                RollingPaperMessageProjection(41L, "서준", null, "생일 축하해!", "LIME_DOT_GRID", "CAKE", null, null, null),
                RollingPaperMessageProjection(42L, "하린", null, null, "BLUE_DOT_GRID", null, null, "https://v.m4a", 12),
            ),
        )

        val response = service.getRollingPaper(OWNER_ID, FUNDING_ID)

        assertEquals("지유", response.celebrantName)
        assertEquals(2, response.messageCount)
        assertEquals(listOf(41L, 42L), response.messages.map { it.messageId })
        assertEquals("https://v.m4a", response.messages[1].voiceUrl)
        assertEquals(12, response.messages[1].voiceDurationSeconds)
    }

    @Test
    fun `개설자가 아니거나 서프라이즈이거나 결과를 열기 전이면 조회할 수 없다`() {
        givenFunding(funding().also { it.openResult(NOW) })
        assertErrorCode(FundingErrorCode.FUNDING_OWNER_ONLY) { service.getRollingPaper(OTHER_ID, FUNDING_ID) }

        givenFunding(funding(type = FundingType.SURPRISE))
        assertErrorCode(FundingErrorCode.BIRTHDAY_FUNDING_REQUIRED) { service.getRollingPaper(OWNER_ID, FUNDING_ID) }

        givenFunding(funding())
        assertErrorCode(FundingErrorCode.ROLLING_PAPER_NOT_OPENED) { service.getRollingPaper(OWNER_ID, FUNDING_ID) }

        verifyNoInteractions(rollingPaperMessageRepository)
    }

    @Test
    fun `없거나 취소된 펀딩은 찾을 수 없다`() {
        given(fundingRepository.findById(FUNDING_ID)).willReturn(Optional.empty())
        assertErrorCode(FundingErrorCode.FUNDING_NOT_FOUND) { service.getRollingPaper(OWNER_ID, FUNDING_ID) }

        givenFunding(funding(status = FundingStatus.CANCELLED))
        assertErrorCode(FundingErrorCode.FUNDING_NOT_FOUND) { service.getRollingPaper(OWNER_ID, FUNDING_ID) }
    }

    private fun givenFunding(funding: Funding) {
        given(fundingRepository.findById(FUNDING_ID)).willReturn(Optional.of(funding))
    }

    private fun assertErrorCode(errorCode: FundingErrorCode, block: () -> Unit) {
        assertEquals(errorCode, assertFailsWith<BusinessException>(block = block).errorCode)
    }

    private fun funding(
        type: FundingType = FundingType.BIRTHDAY,
        status: FundingStatus = FundingStatus.CLOSED,
    ): Funding =
        Funding(
            ownerId = OWNER_ID,
            recipientId = if (type == FundingType.SURPRISE) OTHER_ID else null,
            type = type,
            title = null,
            targetAmount = 100_000L,
            deadline = NOW.minusDays(1),
            revealAt = if (type == FundingType.SURPRISE) NOW.plusDays(1) else null,
            story = null,
            promise = null,
            product = FundingProductSnapshot(
                sourceWishId = 1L,
                name = "무선 헤드폰",
                imageUrl = "https://image.example.com/product.jpg",
                price = 100_000L,
                url = "https://shop.example.com/product",
            ),
            payoutAccount = FundingPayoutAccountSnapshot(
                sourcePayoutAccountId = null,
                bank = Bank.KAKAO_BANK,
                encryptedAccountNumber = "encrypted-account-number",
                accountHolder = null,
            ),
        ).also { funding ->
            funding.javaClass.getDeclaredField("id").apply {
                isAccessible = true
                set(funding, FUNDING_ID)
            }
            funding.javaClass.getDeclaredField("status").apply {
                isAccessible = true
                set(funding, status)
            }
        }

    companion object {
        private const val FUNDING_ID = 12L
        private const val OWNER_ID = 1L
        private const val OTHER_ID = 2L
        private val NOW = LocalDateTime.of(2026, 11, 20, 12, 0)
    }
}
