package com.botem.domain.funding.service

import com.botem.domain.account.entity.Bank
import com.botem.domain.account.entity.PayoutAccount
import com.botem.domain.funding.dto.request.CreateBirthdayFundingRequest
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.user.entity.User
import com.botem.domain.user.entity.UserStatus
import com.botem.domain.user.exception.UserErrorCode
import com.botem.domain.user.repository.UserRepository
import com.botem.domain.wish.entity.Wish
import com.botem.global.exception.AuthErrorCode
import com.botem.global.exception.BusinessException
import com.botem.global.property.FundingShareProperties
import org.junit.jupiter.api.Test
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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FundingCreationServiceTest {

    private val fundingRepository = mock(FundingRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val resourceResolver = mock(FundingCreationResourceResolver::class.java)
    private val service = FundingCreationService(
        fundingRepository = fundingRepository,
        userRepository = userRepository,
        resourceResolver = resourceResolver,
        fundingShareProperties = FundingShareProperties("https://botem.me/fundings/"),
        clock = Clock.fixed(Instant.parse("2026-11-17T03:00:00Z"), ZoneId.of("Asia/Seoul")),
    )

    @Test
    fun `위시와 기본 계좌를 스냅샷으로 저장해 일반 펀딩을 개설한다`() {
        val wish = wish()
        val account = payoutAccount()
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(user())
        given(fundingRepository.countActiveFundings(USER_ID, NOW)).willReturn(4L)
        given(resourceResolver.resolveWish(USER_ID, WISH_ID)).willReturn(wish)
        given(resourceResolver.resolvePayoutAccount(USER_ID, null)).willReturn(account)
        given(fundingRepository.save(any(Funding::class.java))).willAnswer { invocation ->
            (invocation.arguments[0] as Funding).also { setField(it, "id", FUNDING_ID) }
        }

        val response = service.createBirthdayFunding(USER_ID, request())

        assertEquals(FUNDING_ID, response.fundingId)
        assertEquals(FundingType.BIRTHDAY, response.type)
        assertEquals(FundingStatus.FUNDING, response.status)
        assertEquals("https://botem.me/fundings/$FUNDING_ID", response.shareUrl)
        val saved = org.mockito.ArgumentCaptor.forClass(Funding::class.java)
        verify(fundingRepository).save(saved.capture())
        assertEquals(WISH_ID, saved.value.product.sourceWishId)
        assertEquals("무선 헤드폰", saved.value.product.name)
        assertEquals(ACCOUNT_ID, saved.value.payoutAccount.sourcePayoutAccountId)
        assertEquals("encrypted-account-number", saved.value.payoutAccount.encryptedAccountNumber)
        assertEquals(LocalDateTime.of(2026, 11, 30, 23, 59, 59, 999_999_000), saved.value.deadline)
    }

    @Test
    fun `선택 계좌 ID를 리소스 조회에 전달한다`() {
        val request = request(payoutAccountId = ACCOUNT_ID)
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(user())
        given(fundingRepository.countActiveFundings(USER_ID, NOW)).willReturn(0L)
        given(resourceResolver.resolveWish(USER_ID, WISH_ID)).willReturn(wish())
        given(resourceResolver.resolvePayoutAccount(USER_ID, ACCOUNT_ID)).willReturn(payoutAccount())
        given(fundingRepository.save(any(Funding::class.java))).willAnswer { it.arguments[0] as Funding }

        service.createBirthdayFunding(USER_ID, request)

        verify(resourceResolver).resolvePayoutAccount(USER_ID, ACCOUNT_ID)
    }

    @Test
    fun `사용자가 없으면 개설을 거부한다`() {
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(null)

        val exception = assertFailsWith<BusinessException> {
            service.createBirthdayFunding(USER_ID, request())
        }

        assertEquals(UserErrorCode.USER_NOT_FOUND, exception.errorCode)
        verify(fundingRepository, never()).countActiveFundings(USER_ID, NOW)
    }

    @Test
    fun `잠금으로 조회한 사용자가 탈퇴 상태이면 개설을 거부한다`() {
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(user(UserStatus.WITHDRAWN))

        val exception = assertFailsWith<BusinessException> {
            service.createBirthdayFunding(USER_ID, request())
        }

        assertEquals(AuthErrorCode.WITHDRAWN_USER, exception.errorCode)
        verify(fundingRepository, never()).countActiveFundings(USER_ID, NOW)
        verify(fundingRepository, never()).save(any(Funding::class.java))
    }

    @Test
    fun `진행 중 펀딩이 5개이면 개설을 거부한다`() {
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(user())
        given(fundingRepository.countActiveFundings(USER_ID, NOW)).willReturn(5L)

        val exception = assertFailsWith<BusinessException> {
            service.createBirthdayFunding(USER_ID, request())
        }

        assertEquals(FundingErrorCode.ACTIVE_FUNDING_LIMIT_EXCEEDED, exception.errorCode)
        verify(resourceResolver, never()).resolveWish(USER_ID, WISH_ID)
        verify(fundingRepository, never()).save(any(Funding::class.java))
    }

    private fun request(payoutAccountId: Long? = null): CreateBirthdayFundingRequest =
        CreateBirthdayFundingRequest(
            wishId = WISH_ID,
            payoutAccountId = payoutAccountId,
            title = "생일 선물 펀딩",
            targetAmount = 100_000L,
            deadline = LocalDate.of(2026, 11, 30),
            story = "새 헤드폰이 필요해요",
            promise = "인증할게요",
        )

    private fun user(status: UserStatus = UserStatus.ACTIVE): User =
        User(
            kakaoId = 1234L,
            nickname = "지유",
            profileImageUrl = null,
            status = status,
        )

    private fun wish(): Wish =
        Wish(
            userId = USER_ID,
            productName = "무선 헤드폰",
            productImageUrl = "https://image.example.com/product.jpg",
            price = 100_000L,
            productUrl = "https://shop.example.com/product",
        )
            .also { setField(it, "id", WISH_ID) }

    private fun payoutAccount(): PayoutAccount =
        PayoutAccount(
            userId = USER_ID,
            bank = Bank.KAKAO_BANK,
            encryptedAccountNumber = "encrypted-account-number",
            accountHolder = "지유",
            isDefault = true,
        )
            .also { setField(it, "id", ACCOUNT_ID) }

    private fun setField(target: Any, fieldName: String, value: Any?) {
        target.javaClass.getDeclaredField(fieldName).apply {
            isAccessible = true
            set(target, value)
        }
    }

    companion object {
        private const val USER_ID = 1L
        private const val WISH_ID = 2L
        private const val ACCOUNT_ID = 3L
        private const val FUNDING_ID = 4L
        private val NOW = LocalDateTime.of(2026, 11, 17, 12, 0)
    }
}
