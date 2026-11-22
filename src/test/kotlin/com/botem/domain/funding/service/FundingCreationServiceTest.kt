package com.botem.domain.funding.service

import com.botem.domain.account.entity.Bank
import com.botem.domain.funding.dto.request.CreateBirthdayFundingRequest
import com.botem.domain.funding.dto.request.CreateSurpriseFundingRequest
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.user.entity.User
import com.botem.domain.user.entity.UserStatus
import com.botem.domain.user.exception.UserErrorCode
import com.botem.domain.user.repository.UserRepository
import com.botem.domain.user.service.ProfileImageService
import com.botem.domain.user.service.UserService
import com.botem.domain.wish.entity.Wish
import com.botem.global.exception.AuthErrorCode
import com.botem.global.exception.BusinessException
import com.botem.global.property.FundingShareProperties
import com.botem.global.property.WishProfileProperties
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

class FundingCreationServiceTest {

    private val fundingRepository = mock(FundingRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val resourceResolver = mock(FundingCreationResourceResolver::class.java)
    private val service = FundingCreationService(
        fundingRepository = fundingRepository,
        userService = UserService(userRepository, mock(WishProfileProperties::class.java), mock(ProfileImageService::class.java)),
        resourceResolver = resourceResolver,
        fundingShareProperties = FundingShareProperties("https://botem.me/share/"),
        clock = Clock.fixed(Instant.parse("2026-11-17T03:00:00Z"), ZoneId.of("Asia/Seoul")),
    )

    @Test
    fun `위시와 입력한 계좌를 스냅샷으로 저장해 일반 펀딩을 개설한다`() {
        val snapshot = FundingPayoutAccountSnapshot(null, Bank.TOSS_BANK, "encrypted-account-number", null)
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(user())
        given(fundingRepository.countActiveFundings(USER_ID, NOW)).willReturn(4L)
        given(resourceResolver.resolveWish(USER_ID, WISH_ID)).willReturn(wish())
        given(resourceResolver.resolveDirectPayoutAccount(Bank.TOSS_BANK, ACCOUNT_NUMBER)).willReturn(snapshot)
        given(fundingRepository.save(any(Funding::class.java))).willAnswer { invocation ->
            (invocation.arguments[0] as Funding).also { setField(it, "id", FUNDING_ID) }
        }

        val response = service.createBirthdayFunding(USER_ID, request())

        assertEquals(FUNDING_ID, response.fundingId)
        assertEquals(FundingType.BIRTHDAY, response.type)
        assertEquals(FundingStatus.FUNDING, response.status)
        // 공유 링크는 순차 ID가 아니라 랜덤 공유 키로 만든다
        assertTrue(Funding.isShareKeyFormat(response.shareKey))
        assertEquals("https://botem.me/share/${response.shareKey}", response.shareUrl)
        val saved = org.mockito.ArgumentCaptor.forClass(Funding::class.java)
        verify(fundingRepository).save(saved.capture())
        assertEquals(WISH_ID, saved.value.product.sourceWishId)
        assertEquals("무선 헤드폰", saved.value.product.name)
        assertEquals("BOTEM 브랜드샵", saved.value.product.sellerName)
        assertEquals(snapshot, saved.value.payoutAccount)
        assertNull(saved.value.title)
        assertEquals(LocalDateTime.of(2026, 11, 30, 23, 59, 59, 999_999_000), saved.value.deadline)
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

    @Test
    fun `사용자가 입력한 일정으로 서프라이즈 펀딩을 개설한다`() {
        givenSurpriseCreatable()
        given(fundingRepository.save(any(Funding::class.java))).willAnswer { invocation ->
            (invocation.arguments[0] as Funding).also { setField(it, "id", FUNDING_ID) }
        }

        val response = service.createSurpriseFunding(USER_ID, surpriseRequest())

        assertEquals(FundingType.SURPRISE, response.type)
        assertEquals(FundingStatus.FUNDING, response.status)
        // 공유 링크는 순차 ID가 아니라 랜덤 공유 키로 만든다
        assertTrue(Funding.isShareKeyFormat(response.shareKey))
        assertEquals("https://botem.me/share/${response.shareKey}", response.shareUrl)
        val saved = org.mockito.ArgumentCaptor.forClass(Funding::class.java)
        verify(fundingRepository).save(saved.capture())
        assertEquals(USER_ID, saved.value.ownerId)
        assertEquals(RECIPIENT_ID, saved.value.recipientId)
        assertNull(saved.value.title)
        assertNull(saved.value.story)
        assertEquals(WISH_ID, saved.value.product.sourceWishId)
        assertEquals(SURPRISE_PAYOUT_ACCOUNT, saved.value.payoutAccount)
        assertEquals(LocalDateTime.of(2026, 12, 12, 23, 59, 59, 999_999_000), saved.value.deadline)
        assertEquals(LocalDateTime.of(2026, 12, 15, 0, 0), saved.value.revealAt)
        assertEquals(saved.value.deadline, response.deadline)
        assertEquals(saved.value.revealAt, response.revealAt)
        assertFalse(saved.value.showOnRecipientProfile)
        assertEquals(true, saved.value.notifyRecipientFriends)
    }

    @Test
    fun `자기 자신을 받는 사람으로 지정하면 서프라이즈 개설을 거부한다`() {
        val exception = assertFailsWith<BusinessException> {
            service.createSurpriseFunding(USER_ID, surpriseRequest(recipientId = USER_ID))
        }

        assertEquals(FundingErrorCode.SURPRISE_SELF_RECIPIENT_NOT_ALLOWED, exception.errorCode)
        verify(userRepository, never()).findByIdForUpdate(USER_ID)
    }

    @Test
    fun `개설하는 사람이 없거나 탈퇴했으면 서프라이즈 개설을 거부한다`() {
        given(userRepository.findByIdForUpdate(RECIPIENT_ID)).willReturn(user())

        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(null)
        val notFound = assertFailsWith<BusinessException> {
            service.createSurpriseFunding(USER_ID, surpriseRequest())
        }
        assertEquals(UserErrorCode.USER_NOT_FOUND, notFound.errorCode)

        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(user(UserStatus.WITHDRAWN))
        val withdrawn = assertFailsWith<BusinessException> {
            service.createSurpriseFunding(USER_ID, surpriseRequest())
        }
        assertEquals(AuthErrorCode.WITHDRAWN_USER, withdrawn.errorCode)

        verify(resourceResolver, never()).resolveWish(RECIPIENT_ID, WISH_ID)
        verify(fundingRepository, never()).save(any(Funding::class.java))
    }

    @Test
    fun `받는 사람이 없거나 탈퇴했으면 서프라이즈 개설을 거부한다`() {
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(user())

        listOf(null, user(UserStatus.WITHDRAWN)).forEach { recipient ->
            given(userRepository.findByIdForUpdate(RECIPIENT_ID)).willReturn(recipient)

            val exception = assertFailsWith<BusinessException> {
                service.createSurpriseFunding(USER_ID, surpriseRequest())
            }

            assertEquals(UserErrorCode.USER_NOT_FOUND, exception.errorCode)
        }
        verify(fundingRepository, never()).save(any(Funding::class.java))
    }

    @Test
    fun `받는 사람에게 진행 중인 서프라이즈가 있으면 개설을 거부한다`() {
        givenSurpriseCreatable(surpriseExists = true)

        val exception = assertFailsWith<BusinessException> {
            service.createSurpriseFunding(USER_ID, surpriseRequest())
        }

        assertEquals(FundingErrorCode.SURPRISE_FUNDING_ALREADY_EXISTS, exception.errorCode)
        verify(resourceResolver, never()).resolveWish(RECIPIENT_ID, WISH_ID)
    }

    @Test
    fun `받는 사람 생일 정보가 없어도 입력한 일정으로 서프라이즈 펀딩을 개설한다`() {
        givenSurpriseCreatable(recipientBirthday = null)
        given(fundingRepository.save(any(Funding::class.java))).willAnswer { it.arguments[0] as Funding }

        service.createSurpriseFunding(USER_ID, surpriseRequest())

        val saved = org.mockito.ArgumentCaptor.forClass(Funding::class.java)
        verify(fundingRepository).save(saved.capture())
        assertEquals(LocalDateTime.of(2026, 12, 12, 23, 59, 59, 999_999_000), saved.value.deadline)
        assertEquals(LocalDateTime.of(2026, 12, 15, 0, 0), saved.value.revealAt)
    }

    @Test
    fun `공개일이 마감일과 같거나 이전이면 서프라이즈 개설을 거부한다`() {
        listOf(LocalDate.of(2026, 12, 12), LocalDate.of(2026, 12, 11)).forEach { revealAt ->
            givenSurpriseCreatable()

            val exception = assertFailsWith<BusinessException> {
                service.createSurpriseFunding(USER_ID, surpriseRequest(revealAt = revealAt))
            }

            assertEquals(com.botem.global.exception.CommonErrorCode.INVALID_INPUT_VALUE, exception.errorCode)
        }
        verify(fundingRepository, never()).save(any(Funding::class.java))
    }

    private fun givenSurpriseCreatable(
        surpriseExists: Boolean = false,
        recipientBirthday: java.time.MonthDay? = java.time.MonthDay.of(12, 1),
    ) {
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(user())
        given(userRepository.findByIdForUpdate(RECIPIENT_ID)).willReturn(user(birthday = recipientBirthday))
        given(fundingRepository.countActiveFundings(USER_ID, NOW)).willReturn(0L)
        given(
            fundingRepository.existsByRecipientIdAndTypeAndRevealAtAfterAndStatusNotIn(
                RECIPIENT_ID,
                FundingType.SURPRISE,
                NOW,
                setOf(FundingStatus.COMPLETED, FundingStatus.CANCELLED),
            ),
        ).willReturn(surpriseExists)
        given(resourceResolver.resolveWish(RECIPIENT_ID, WISH_ID)).willReturn(wish())
        given(resourceResolver.resolveDirectPayoutAccount(Bank.TOSS_BANK, ACCOUNT_NUMBER))
            .willReturn(SURPRISE_PAYOUT_ACCOUNT)
    }

    private fun surpriseRequest(
        recipientId: Long = RECIPIENT_ID,
        deadline: LocalDate = LocalDate.of(2026, 12, 12),
        revealAt: LocalDate = LocalDate.of(2026, 12, 15),
    ): CreateSurpriseFundingRequest =
        CreateSurpriseFundingRequest(
            recipientId = recipientId,
            wishId = WISH_ID,
            bank = Bank.TOSS_BANK,
            accountNumber = ACCOUNT_NUMBER,
            targetAmount = 100_000L,
            deadline = deadline,
            revealAt = revealAt,
            showOnRecipientProfile = false,
        )

    private fun request(): CreateBirthdayFundingRequest =
        CreateBirthdayFundingRequest(
            wishId = WISH_ID,
            bank = Bank.TOSS_BANK,
            accountNumber = ACCOUNT_NUMBER,
            targetAmount = 100_000L,
            deadline = LocalDate.of(2026, 11, 30),
            story = "새 헤드폰이 필요해요",
            promise = "인증할게요",
        )

    private fun user(
        status: UserStatus = UserStatus.ACTIVE,
        birthday: java.time.MonthDay? = null,
    ): User =
        User(
            kakaoId = 1234L,
            nickname = "지유",
            profileImageUrl = null,
            birthday = birthday,
            status = status,
        )

    private fun wish(): Wish =
        Wish(
            userId = USER_ID,
            productName = "무선 헤드폰",
            productImageUrl = "https://image.example.com/product.jpg",
            price = 100_000L,
            productUrl = "https://shop.example.com/product",
            sellerName = "BOTEM 브랜드샵",
        )
            .also { setField(it, "id", WISH_ID) }

    private fun setField(target: Any, fieldName: String, value: Any?) {
        target.javaClass.getDeclaredField(fieldName).apply {
            isAccessible = true
            set(target, value)
        }
    }

    companion object {
        private const val ACCOUNT_NUMBER = "100159014821"
        private const val USER_ID = 1L
        private const val WISH_ID = 2L
        private const val FUNDING_ID = 4L
        private const val RECIPIENT_ID = 5L
        private val NOW = LocalDateTime.of(2026, 11, 17, 12, 0)
        private val SURPRISE_PAYOUT_ACCOUNT =
            FundingPayoutAccountSnapshot(null, Bank.TOSS_BANK, "encrypted-account-number", null)
    }
}
