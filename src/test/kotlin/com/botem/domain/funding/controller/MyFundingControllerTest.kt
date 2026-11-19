package com.botem.domain.funding.controller

import com.botem.domain.funding.dto.response.ClosedFundingGiftVerificationStatus
import com.botem.domain.funding.dto.response.ClosedFundingListResponse
import com.botem.domain.funding.dto.response.ClosedFundingResponse
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.service.ClosedFundingService
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
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

@WebMvcTest(controllers = [MyFundingController::class])
class MyFundingControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var closedFundingService: ClosedFundingService

    @Test
    fun `로그인 사용자의 마감된 펀딩 목록을 조회한다`() {
        given(closedFundingService.getClosedFundings(USER_ID)).willReturn(
            ClosedFundingListResponse(
                listOf(
                    ClosedFundingResponse(
                        fundingId = 12L,
                        type = FundingType.BIRTHDAY,
                        productName = "에어팟 프로 2",
                        productImageUrl = "https://image.example.com/product.jpg",
                        deadline = LocalDateTime.of(2025, 10, 13, 23, 59, 59),
                        targetReached = true,
                        participantCount = 8L,
                        recipientName = null,
                        giftVerificationStatus = ClosedFundingGiftVerificationStatus.REQUIRED,
                    ),
                ),
            ),
        )

        mockMvc.perform(
            get("/api/users/me/fundings/closed")
                .with(authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList()))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.fundings[0].fundingId").value(12))
            .andExpect(jsonPath("$.data.fundings[0].targetReached").value(true))
            .andExpect(jsonPath("$.data.fundings[0].recipientName").doesNotExist())
            .andExpect(jsonPath("$.data.fundings[0].giftVerificationStatus").value("REQUIRED"))
    }

    @Test
    fun `로그인하지 않으면 마감된 펀딩 목록을 조회할 수 없다`() {
        mockMvc.perform(get("/api/users/me/fundings/closed"))
            .andExpect(status().isUnauthorized)

        verifyNoInteractions(closedFundingService)
    }

    companion object {
        private const val USER_ID = 1L
    }
}
