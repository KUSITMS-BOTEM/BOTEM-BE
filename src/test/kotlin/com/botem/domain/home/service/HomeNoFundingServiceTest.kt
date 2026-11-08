package com.botem.domain.home.service

import com.botem.domain.funding.entity.FundingType
import com.botem.domain.home.dto.response.HomeFundingCardResponse
import com.botem.domain.home.dto.response.HomeFundingCardType
import com.botem.domain.home.dto.response.HomeFundingListResponse
import com.botem.domain.home.dto.response.HomeNoFundingState
import com.botem.domain.home.exception.HomeErrorCode
import com.botem.domain.user.entity.User
import com.botem.domain.user.repository.UserRepository
import com.botem.domain.wish.entity.Wish
import com.botem.domain.wish.repository.WishRepository
import com.botem.global.exception.BusinessException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import java.time.Clock
import java.time.Instant
import java.time.MonthDay
import java.time.ZoneId
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeNoFundingServiceTest {

    private val homeFundingService = mock(HomeFundingService::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val wishRepository = mock(WishRepository::class.java)
    private val service = HomeNoFundingService(
        homeFundingService = homeFundingService,
        userRepository = userRepository,
        wishRepository = wishRepository,
        clock = Clock.fixed(Instant.parse("2026-11-08T03:00:00Z"), ZoneId.of("Asia/Seoul")),
    )

    @Test
    fun `펀딩이 없고 위시가 있으면 최신순 위시와 생일 D-day를 반환한다`() {
        val latestWish = wish(id = 2L, productName = "무선 헤드폰")
        val olderWish = wish(id = 1L, productName = "향수")
        given(homeFundingService.getHomeFundings(USER_ID)).willReturn(emptyHomeFundings())
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(MonthDay.of(12, 1))))
        given(wishRepository.findAllByUserIdOrderByCreatedAtDesc(USER_ID))
            .willReturn(listOf(latestWish, olderWish))

        val response = service.getNoFundingHome(USER_ID)

        assertEquals(HomeNoFundingState.WISH_RECOMMENDATION, response.state)
        assertEquals("지유", response.nickname)
        assertEquals(23L, response.birthdayDday)
        assertEquals(listOf(2L, 1L), response.wishes.map { it.wishId })
        assertEquals("무선 헤드폰", response.wishes.first().productName)
    }

    @Test
    fun `펀딩과 위시가 없고 생일까지 30일을 초과하면 빈 상태와 null D-day를 반환한다`() {
        given(homeFundingService.getHomeFundings(USER_ID)).willReturn(emptyHomeFundings())
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(MonthDay.of(12, 9))))
        given(wishRepository.findAllByUserIdOrderByCreatedAtDesc(USER_ID)).willReturn(emptyList())

        val response = service.getNoFundingHome(USER_ID)

        assertEquals(HomeNoFundingState.EMPTY, response.state)
        assertNull(response.birthdayDday)
        assertEquals(emptyList(), response.wishes)
    }

    @Test
    fun `생일까지 정확히 30일이면 D-day를 반환한다`() {
        given(homeFundingService.getHomeFundings(USER_ID)).willReturn(emptyHomeFundings())
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(MonthDay.of(12, 8))))
        given(wishRepository.findAllByUserIdOrderByCreatedAtDesc(USER_ID)).willReturn(emptyList())

        val response = service.getNoFundingHome(USER_ID)

        assertEquals(30L, response.birthdayDday)
    }

    @Test
    fun `홈에 표시할 펀딩이 있으면 사용자와 위시를 조회하지 않고 예외를 반환한다`() {
        given(homeFundingService.getHomeFundings(USER_ID)).willReturn(
            HomeFundingListResponse(
                fundingCards = listOf(homeFundingCard()),
                activeFundingCount = 1,
                canOpenFunding = true,
            ),
        )

        val exception = assertThrows<BusinessException> {
            service.getNoFundingHome(USER_ID)
        }

        assertEquals(HomeErrorCode.FUNDING_CARD_EXISTS, exception.errorCode)
        verifyNoInteractions(userRepository, wishRepository)
    }

    private fun emptyHomeFundings(): HomeFundingListResponse =
        HomeFundingListResponse(
            fundingCards = emptyList(),
            activeFundingCount = 0,
            canOpenFunding = true,
        )

    private fun homeFundingCard(): HomeFundingCardResponse =
        HomeFundingCardResponse(
            fundingId = 10L,
            cardType = HomeFundingCardType.IN_PROGRESS,
            fundingType = FundingType.BIRTHDAY,
            productName = "무선 헤드폰",
            productImageUrl = "https://image.example.com/product.jpg",
            remainingDays = 7L,
            confirmedPercentage = 50,
            pendingPercentage = 10,
            pendingContributionCount = 1L,
        )

    private fun user(birthday: MonthDay): User =
        User(
            kakaoId = 12345L,
            nickname = "지유",
            profileImageUrl = null,
            birthday = birthday,
        )

    private fun wish(
        id: Long,
        productName: String,
    ): Wish =
        Wish(
            userId = USER_ID,
            productName = productName,
            productImageUrl = "https://image.example.com/$id.jpg",
            price = 100_000L,
            productUrl = "https://shop.example.com/$id",
        ).also { setField(it, "id", id) }

    private fun setField(target: Any, fieldName: String, value: Any) {
        target.javaClass.getDeclaredField(fieldName).apply {
            isAccessible = true
            set(target, value)
        }
    }

    companion object {
        private const val USER_ID = 1L
    }
}
