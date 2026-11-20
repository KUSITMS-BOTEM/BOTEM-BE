package com.botem.domain.funding.controller

import org.junit.jupiter.api.BeforeEach
import com.botem.domain.funding.service.FundingShareKeyResolver
import com.botem.domain.funding.dto.request.UpdateMyContributionRequest
import com.botem.domain.funding.dto.response.MyContributionListResponse
import com.botem.domain.funding.dto.response.MyContributionResponse
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.service.MyContributionService
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

@WebMvcTest(controllers = [MyContributionController::class])
class MyContributionControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean

    private lateinit var fundingShareKeyResolver: FundingShareKeyResolver


    @MockitoBean
    private lateinit var myContributionService: MyContributionService

    @BeforeEach

    fun stubShareKey() {

        given(fundingShareKeyResolver.fundingIdOf(SHARE_KEY)).willReturn(FUNDING_ID)

    }


    @Test
    fun `내 보탬을 조회하고 수정한다`() {
        given(myContributionService.getMyContributions(USER_ID, FUNDING_ID))
            .willReturn(MyContributionListResponse(editable = true, contributions = listOf(response())))
        given(
            myContributionService.updateMyContribution(
                USER_ID,
                FUNDING_ID,
                CONTRIBUTION_ID,
                UpdateMyContributionRequest("민지", "진짜 축하해!", "BLUE_DOT_GRID"),
            ),
        ).willReturn(response())

        mockMvc.perform(get("/api/fundings/share/$SHARE_KEY/contributions/me").with(loggedIn()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.editable").value(true))
            .andExpect(jsonPath("$.data.contributions[0].status").value("PENDING_CONFIRMATION"))
            .andExpect(jsonPath("$.data.contributions[0].photoUrl").value(PHOTO_URL))
        mockMvc.perform(
            patch("/api/fundings/share/$SHARE_KEY/contributions/$CONTRIBUTION_ID")
                .with(loggedIn())
                .with(csrf())
                .contentType("application/json")
                .content("""{ "participantName": "민지", "message": "진짜 축하해!", "paperTemplateCode": "BLUE_DOT_GRID" }"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.contributionId").value(CONTRIBUTION_ID))
    }

    @Test
    fun `메시지가 비어 있으면 수정하지 않는다`() {
        mockMvc.perform(
            patch("/api/fundings/share/$SHARE_KEY/contributions/$CONTRIBUTION_ID")
                .with(loggedIn())
                .with(csrf())
                .contentType("application/json")
                .content("""{ "participantName": "민지", "message": " ", "paperTemplateCode": "BLUE_DOT_GRID" }"""),
        )
            .andExpect(status().isBadRequest)

        verifyNoInteractions(myContributionService)
    }

    private fun response(): MyContributionResponse =
        MyContributionResponse(
            contributionId = CONTRIBUTION_ID,
            participantName = "민지",
            amount = 30_000L,
            status = ContributionStatus.PENDING_CONFIRMATION,
            excludedFromResult = false,
            message = "진짜 축하해!",
            paperTemplateCode = "BLUE_DOT_GRID",
            decorationCode = "PARTY_POPPER",
            photoUrl = PHOTO_URL,
            voiceUrl = null,
            voiceDurationSeconds = null,
            voiceTranscript = null,
            participatedAt = LocalDateTime.of(2026, 10, 13, 11, 20),
            payoutAccount = null,
        )

    private fun loggedIn() = authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList()))

    companion object {
        private const val SHARE_KEY = "0123456789abcdef0123456789abcdef"
        private const val USER_ID = 1L
        private const val FUNDING_ID = 10L
        private const val CONTRIBUTION_ID = 21L
        private const val PHOTO_URL = "https://image.example.com/photo.jpg"
    }
}
