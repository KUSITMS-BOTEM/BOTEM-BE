package com.botem.domain.funding.controller

import com.botem.domain.funding.dto.response.FundingArchiveCardResponse
import com.botem.domain.funding.dto.response.FundingArchiveRelationship
import com.botem.domain.funding.dto.response.FundingArchiveResponse
import com.botem.domain.funding.dto.response.FundingArchiveTab
import com.botem.domain.funding.dto.response.FundingArchiveTabCounts
import com.botem.domain.funding.dto.response.FundingArchiveUserResponse
import com.botem.domain.funding.dto.response.FundingArchiveYearResponse
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.service.FundingArchiveService
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
    private lateinit var fundingArchiveService: FundingArchiveService

    @Test
    fun `로그인 사용자의 지난 펀딩 아카이브를 탭 기준으로 조회한다`() {
        given(fundingArchiveService.getArchive(USER_ID, FundingArchiveTab.PARTICIPATED)).willReturn(
            FundingArchiveResponse(
                tabCounts = FundingArchiveTabCounts(all = 3, received = 1, participated = 1),
                years = listOf(
                    FundingArchiveYearResponse(
                        year = 2026,
                        fundingCount = 1,
                        fundings = listOf(
                            FundingArchiveCardResponse(
                                fundingId = 12L,
                                relationship = FundingArchiveRelationship.PARTICIPATED,
                                fundingType = FundingType.BIRTHDAY,
                                opener = FundingArchiveUserResponse("서준", null),
                                celebrantName = "지유",
                                productName = "에어팟 프로 2",
                                productImageUrl = "https://image.example.com/product.jpg",
                                deadline = LocalDateTime.of(2026, 10, 13, 23, 59, 59),
                                rollingPaperCount = 8L,
                                representativeMessage = null,
                            ),
                        ),
                    ),
                ),
            ),
        )

        mockMvc.perform(
            get("/api/users/me/fundings/closed?tab=PARTICIPATED")
                .with(authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList()))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.tabCounts.all").value(3))
            .andExpect(jsonPath("$.data.tabCounts.participated").value(1))
            .andExpect(jsonPath("$.data.years[0].year").value(2026))
            .andExpect(jsonPath("$.data.years[0].fundings[0].fundingId").value(12))
            .andExpect(jsonPath("$.data.years[0].fundings[0].relationship").value("PARTICIPATED"))
    }

    @Test
    fun `로그인하지 않으면 지난 펀딩 아카이브를 조회할 수 없다`() {
        mockMvc.perform(get("/api/users/me/fundings/closed"))
            .andExpect(status().isUnauthorized)

        verifyNoInteractions(fundingArchiveService)
    }

    @Test
    fun `지원하지 않는 탭은 입력값 오류를 반환한다`() {
        mockMvc.perform(
            get("/api/users/me/fundings/closed?tab=OWNER")
                .with(authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList()))),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("COMMON_001"))

        verifyNoInteractions(fundingArchiveService)
    }

    companion object {
        private const val USER_ID = 1L
    }
}
