package com.botem.domain.funding.controller

import com.botem.domain.funding.dto.response.ConfirmAllContributionsResponse
import com.botem.domain.funding.dto.response.ContributionDecisionResponse
import com.botem.domain.funding.dto.response.FundingProgressResponse
import com.botem.domain.funding.dto.response.PendingContributionListResponse
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.service.DepositConfirmationService
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
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

@WebMvcTest(controllers = [DepositConfirmationController::class])
class DepositConfirmationControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var depositConfirmationService: DepositConfirmationService

    @Test
    fun `개설자가 입금 확인 대기 목록을 조회한다`() {
        given(depositConfirmationService.getPendingContributions(USER_ID, FUNDING_ID))
            .willReturn(PendingContributionListResponse(0, emptyList()))

        mockMvc.perform(get("/api/fundings/{fundingId}/contributions/pending", FUNDING_ID).with(loggedIn()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.pendingContributionCount").value(0))
    }

    @Test
    fun `개설자가 입금을 확인하면 처리 후 진행률을 반환한다`() {
        given(depositConfirmationService.confirm(USER_ID, FUNDING_ID, CONTRIBUTION_ID)).willReturn(
            ContributionDecisionResponse(CONTRIBUTION_ID, ContributionStatus.CONFIRMED, progress()),
        )

        mockMvc.perform(
            post("/api/fundings/{fundingId}/contributions/{contributionId}/confirm", FUNDING_ID, CONTRIBUTION_ID)
                .with(loggedIn())
                .with(csrf()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.data.progress.confirmedPercentage").value(55))
    }

    @Test
    fun `개설자가 미입금 처리와 전체 확인을 요청한다`() {
        given(depositConfirmationService.reject(USER_ID, FUNDING_ID, CONTRIBUTION_ID)).willReturn(
            ContributionDecisionResponse(CONTRIBUTION_ID, ContributionStatus.REJECTED, progress()),
        )
        given(depositConfirmationService.confirmAll(USER_ID, FUNDING_ID))
            .willReturn(ConfirmAllContributionsResponse(2, progress()))

        mockMvc.perform(
            post("/api/fundings/{fundingId}/contributions/{contributionId}/reject", FUNDING_ID, CONTRIBUTION_ID)
                .with(loggedIn())
                .with(csrf()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("REJECTED"))

        mockMvc.perform(
            post("/api/fundings/{fundingId}/contributions/confirm-all", FUNDING_ID)
                .with(loggedIn())
                .with(csrf()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.confirmedCount").value(2))
    }

    @Test
    fun `로그인하지 않으면 입금 확인 API를 사용할 수 없다`() {
        mockMvc.perform(get("/api/fundings/{fundingId}/contributions/pending", FUNDING_ID))
            .andExpect(status().isUnauthorized)

        verifyNoInteractions(depositConfirmationService)
    }

    private fun loggedIn() = authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList()))

    private fun progress() = FundingProgressResponse(
        confirmedPercentage = 55,
        pendingPercentage = 15,
        pendingContributionCount = 1L,
        targetReached = false,
    )

    companion object {
        private const val USER_ID = 1L
        private const val FUNDING_ID = 12L
        private const val CONTRIBUTION_ID = 31L
    }
}
