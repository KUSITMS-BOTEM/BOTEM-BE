package com.botem.domain.funding.controller

import com.botem.domain.funding.dto.response.FundingResultResponse
import com.botem.domain.funding.service.FundingCreationService
import com.botem.domain.funding.service.FundingResultService
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

@WebMvcTest(controllers = [FundingController::class])
class FundingControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var fundingCreationService: FundingCreationService

    @MockitoBean
    private lateinit var fundingResultService: FundingResultService

    @Test
    fun `로그인한 개설자가 펀딩 결과를 조회한다`() {
        given(fundingResultService.getResult(USER_ID, FUNDING_ID)).willReturn(resultResponse(resultOpenedAt = null))

        mockMvc.perform(
            get("/api/fundings/{fundingId}/result", FUNDING_ID)
                .with(authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList()))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.fundingId").value(FUNDING_ID))
            .andExpect(jsonPath("$.data.confirmedAmount").value(80_000L))
            .andExpect(jsonPath("$.data.achievementPercentage").value(80))
            .andExpect(jsonPath("$.data.targetReached").value(false))
            .andExpect(jsonPath("$.data.participantCount").value(4L))
            .andExpect(jsonPath("$.data.resultOpenedAt").isEmpty)

        verify(fundingResultService).getResult(USER_ID, FUNDING_ID)
    }

    @Test
    fun `로그인한 개설자가 펀딩 결과를 팡 연다`() {
        given(fundingResultService.openResult(USER_ID, FUNDING_ID)).willReturn(resultResponse(OPENED_AT))

        mockMvc.perform(
            post("/api/fundings/{fundingId}/result/open", FUNDING_ID)
                .with(authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList())))
                .with(csrf()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.fundingId").value(FUNDING_ID))
            .andExpect(jsonPath("$.data.resultOpenedAt").value("2026-11-15T00:00:00"))

        verify(fundingResultService).openResult(USER_ID, FUNDING_ID)
    }

    @Test
    fun `인증 없이 펀딩 결과를 조회하면 401을 반환한다`() {
        mockMvc.perform(get("/api/fundings/{fundingId}/result", FUNDING_ID))
            .andExpect(status().isUnauthorized)

        verifyNoInteractions(fundingResultService)
    }

    private fun resultResponse(resultOpenedAt: LocalDateTime?): FundingResultResponse =
        FundingResultResponse(
            fundingId = FUNDING_ID,
            productName = "무선 헤드폰",
            productImageUrl = "https://image.example.com/product.jpg",
            targetAmount = 100_000L,
            confirmedAmount = 80_000L,
            achievementPercentage = 80,
            targetReached = false,
            participantCount = 4L,
            resultOpenedAt = resultOpenedAt,
        )

    companion object {
        private const val USER_ID = 1L
        private const val FUNDING_ID = 12L
        private val OPENED_AT = LocalDateTime.of(2026, 11, 15, 0, 0)
    }
}
