package com.botem.domain.home.controller

import com.botem.domain.funding.entity.FundingType
import com.botem.domain.home.dto.response.GiftVerificationBannerResponse
import com.botem.domain.home.dto.response.GiftVerificationTargetListResponse
import com.botem.domain.home.dto.response.GiftVerificationTargetResponse
import com.botem.domain.home.dto.response.HomeFundingCardResponse
import com.botem.domain.home.dto.response.HomeFundingCardType
import com.botem.domain.home.dto.response.HomeFundingListResponse
import com.botem.domain.home.dto.response.HomeNoFundingResponse
import com.botem.domain.home.dto.response.HomeNoFundingState
import com.botem.domain.home.dto.response.HomeWishCardResponse
import com.botem.domain.home.dto.response.HomeWishPageResponse
import com.botem.domain.home.dto.response.SurpriseRevealCardListResponse
import com.botem.domain.home.dto.response.SurpriseRevealCardResponse
import com.botem.domain.home.exception.HomeErrorCode
import com.botem.domain.home.service.HomeFundingService
import com.botem.domain.home.service.HomeGiftVerificationService
import com.botem.domain.home.service.HomeNoFundingService
import com.botem.domain.home.service.HomeSurpriseRevealService
import com.botem.global.exception.BusinessException
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

@WebMvcTest(controllers = [HomeController::class])
class HomeControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var homeFundingService: HomeFundingService

    @MockitoBean
    private lateinit var homeNoFundingService: HomeNoFundingService

    @MockitoBean
    private lateinit var homeGiftVerificationService: HomeGiftVerificationService

    @MockitoBean
    private lateinit var homeSurpriseRevealService: HomeSurpriseRevealService

    @Test
    fun `로그인 사용자의 홈 펀딩 카드를 조회한다`() {
        given(homeFundingService.getHomeFundings(USER_ID)).willReturn(
            HomeFundingListResponse(
                fundingCards = listOf(
                    HomeFundingCardResponse(
                        fundingId = 10L,
                        cardType = HomeFundingCardType.IN_PROGRESS,
                        fundingType = FundingType.BIRTHDAY,
                        productName = "무선 헤드폰",
                        productImageUrl = "https://image.example.com/product.jpg",
                        remainingDays = 7L,
                        confirmedPercentage = 55,
                        pendingPercentage = 15,
                        pendingContributionCount = 2L,
                    ),
                ),
                activeFundingCount = 1,
                canOpenFunding = true,
            ),
        )

        mockMvc.perform(
            get("/api/home/fundings")
                .with(authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList()))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.fundingCards[0].fundingId").value(10L))
            .andExpect(jsonPath("$.data.fundingCards[0].cardType").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.data.fundingCards[0].remainingDays").value(7L))
            .andExpect(jsonPath("$.data.fundingCards[0].confirmedPercentage").value(55))
            .andExpect(jsonPath("$.data.fundingCards[0].pendingPercentage").value(15))
            .andExpect(jsonPath("$.data.activeFundingCount").value(1))
            .andExpect(jsonPath("$.data.canOpenFunding").value(true))

        verify(homeFundingService).getHomeFundings(USER_ID)
    }

    @Test
    fun `인증 없이 홈 펀딩 카드를 조회하면 401을 반환한다`() {
        mockMvc.perform(get("/api/home/fundings"))
            .andExpect(status().isUnauthorized)

        verifyNoInteractions(homeFundingService)
    }

    @Test
    fun `로그인 사용자의 펀딩 없는 홈 상태를 조회한다`() {
        given(homeNoFundingService.getNoFundingHome(USER_ID, 0, 10)).willReturn(
            HomeNoFundingResponse(
                state = HomeNoFundingState.WISH_RECOMMENDATION,
                nickname = "지유",
                birthdayDday = 23L,
                wishes = HomeWishPageResponse(
                    content = listOf(
                        HomeWishCardResponse(
                            wishId = 21L,
                            productName = "무선 헤드폰",
                            productImageUrl = "https://image.example.com/product.jpg",
                            price = 100_000L,
                            productUrl = "https://shop.example.com/product",
                        ),
                    ),
                    page = 0,
                    size = 10,
                    totalElements = 12L,
                    hasNext = true,
                ),
            ),
        )

        mockMvc.perform(
            get("/api/home/no-funding")
                .with(authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList()))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.state").value("WISH_RECOMMENDATION"))
            .andExpect(jsonPath("$.data.nickname").value("지유"))
            .andExpect(jsonPath("$.data.birthdayDday").value(23L))
            .andExpect(jsonPath("$.data.wishes.content[0].wishId").value(21L))
            .andExpect(jsonPath("$.data.wishes.content[0].price").value(100_000L))
            .andExpect(jsonPath("$.data.wishes.totalElements").value(12L))
            .andExpect(jsonPath("$.data.wishes.hasNext").value(true))

        verify(homeNoFundingService).getNoFundingHome(USER_ID, 0, 10)
    }

    @Test
    fun `홈에 표시할 펀딩이 있으면 409를 반환한다`() {
        given(homeNoFundingService.getNoFundingHome(USER_ID, 0, 10))
            .willThrow(BusinessException(HomeErrorCode.FUNDING_CARD_EXISTS))

        mockMvc.perform(
            get("/api/home/no-funding")
                .with(authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList()))),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("HOME_001"))
    }

    @Test
    fun `인증 없이 펀딩 없는 홈 상태를 조회하면 401을 반환한다`() {
        mockMvc.perform(get("/api/home/no-funding"))
            .andExpect(status().isUnauthorized)

        verifyNoInteractions(homeNoFundingService)
    }

    @Test
    fun `로그인 사용자의 선물 인증 배너를 조회한다`() {
        given(homeGiftVerificationService.getBanner(USER_ID)).willReturn(
            GiftVerificationBannerResponse(
                visible = true,
                pendingCount = 2L,
            ),
        )

        mockMvc.perform(
            get("/api/home/gift-verifications/banner")
                .with(authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList()))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.visible").value(true))
            .andExpect(jsonPath("$.data.pendingCount").value(2L))

        verify(homeGiftVerificationService).getBanner(USER_ID)
    }

    @Test
    fun `로그인 사용자의 선물 인증 대상 목록을 조회한다`() {
        given(homeGiftVerificationService.getTargets(USER_ID)).willReturn(
            GiftVerificationTargetListResponse(
                pendingCount = 1,
                targets = listOf(
                    GiftVerificationTargetResponse(
                        giftVerificationId = 7L,
                        fundingId = 12L,
                        productName = "무선 헤드폰",
                        productImageUrl = "https://image.example.com/product.jpg",
                        fundingDeadline = LocalDateTime.of(2026, 11, 1, 23, 59),
                        participantCount = 6L,
                        replacementProductName = null,
                    ),
                ),
            ),
        )

        mockMvc.perform(
            get("/api/home/gift-verifications")
                .with(authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList()))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.pendingCount").value(1))
            .andExpect(jsonPath("$.data.targets[0].giftVerificationId").value(7L))
            .andExpect(jsonPath("$.data.targets[0].fundingId").value(12L))
            .andExpect(jsonPath("$.data.targets[0].participantCount").value(6L))

        verify(homeGiftVerificationService).getTargets(USER_ID)
    }

    @Test
    fun `인증 없이 선물 인증 홈 API를 조회하면 401을 반환한다`() {
        mockMvc.perform(get("/api/home/gift-verifications/banner"))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/home/gift-verifications"))
            .andExpect(status().isUnauthorized)

        verifyNoInteractions(homeGiftVerificationService)
    }

    @Test
    fun `로그인 사용자의 서프라이즈 공개 카드를 조회한다`() {
        given(homeSurpriseRevealService.getCards(USER_ID)).willReturn(
            SurpriseRevealCardListResponse(
                cards = listOf(
                    SurpriseRevealCardResponse(
                        fundingId = 24L,
                        productImageUrl = "https://image.example.com/surprise.jpg",
                        productName = "무선 헤드폰",
                        organizerName = "민지",
                        participantCount = 6L,
                        revealAt = LocalDateTime.of(2026, 11, 10, 0, 0),
                    ),
                ),
            ),
        )

        mockMvc.perform(
            get("/api/home/surprise-reveals")
                .with(authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList()))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.cards[0].fundingId").value(24L))
            .andExpect(jsonPath("$.data.cards[0].productName").value("무선 헤드폰"))
            .andExpect(jsonPath("$.data.cards[0].organizerName").value("민지"))
            .andExpect(jsonPath("$.data.cards[0].participantCount").value(6L))
            .andExpect(jsonPath("$.data.cards[0].revealAt").value("2026-11-10T00:00:00"))

        verify(homeSurpriseRevealService).getCards(USER_ID)
    }

    @Test
    fun `인증 없이 서프라이즈 공개 카드를 조회하면 401을 반환한다`() {
        mockMvc.perform(get("/api/home/surprise-reveals"))
            .andExpect(status().isUnauthorized)

        verifyNoInteractions(homeSurpriseRevealService)
    }

    companion object {
        private const val USER_ID = 1L
    }
}
