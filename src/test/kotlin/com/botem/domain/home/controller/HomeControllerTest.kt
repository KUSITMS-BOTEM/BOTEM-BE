package com.botem.domain.home.controller

import com.botem.domain.funding.entity.FundingType
import com.botem.domain.home.dto.response.HomeFundingCardResponse
import com.botem.domain.home.dto.response.HomeFundingCardType
import com.botem.domain.home.dto.response.HomeFundingListResponse
import com.botem.domain.home.service.HomeFundingService
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

@WebMvcTest(controllers = [HomeController::class])
class HomeControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var homeFundingService: HomeFundingService

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

    companion object {
        private const val USER_ID = 1L
    }
}
