package com.botem.domain.funding.controller

import com.botem.domain.funding.dto.request.AccessGuestContributionRequest
import com.botem.domain.funding.dto.request.UpdateGuestContributionRequest
import com.botem.domain.funding.dto.response.GuestContributionResponse
import com.botem.domain.funding.dto.response.UpdatedGuestContributionResponse
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.service.GuestContributionAccessTokenProvider
import com.botem.domain.funding.service.GuestContributionService
import com.botem.domain.user.repository.UserRepository
import com.botem.global.config.SecurityConfig
import com.botem.global.jwt.JwtAuthenticationEntryPoint
import com.botem.global.jwt.JwtProvider
import com.botem.global.property.AdminProperties
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@WebMvcTest(controllers = [GuestContributionController::class])
@Import(SecurityConfig::class)
@EnableConfigurationProperties(AdminProperties::class)
class GuestContributionControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var guestContributionService: GuestContributionService

    @MockitoBean
    private lateinit var jwtProvider: JwtProvider

    @MockitoBean
    private lateinit var jwtAuthenticationEntryPoint: JwtAuthenticationEntryPoint

    @MockitoBean
    private lateinit var userRepository: UserRepository

    @Test
    fun `로그인 없이 이름과 비밀번호로 비회원 보탬을 조회한다`() {
        val request = AccessGuestContributionRequest("민지", "1234")
        given(guestContributionService.access(FUNDING_ID, request)).willReturn(accessResponse())

        mockMvc.perform(
            post("/api/fundings/{fundingId}/contributions/guest/access", FUNDING_ID)
                .with(csrf())
                .contentType("application/json")
                .content("""{ "participantName": "민지", "guestPassword": "1234" }"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.contributionId").value(CONTRIBUTION_ID))
            .andExpect(jsonPath("$.data.accessToken").value(ACCESS_TOKEN))
            .andExpect(jsonPath("$.data.message").value("생일 축하해!"))
    }

    @Test
    fun `비밀번호가 숫자 4자리가 아니면 조회하지 않는다`() {
        mockMvc.perform(
            post("/api/fundings/{fundingId}/contributions/guest/access", FUNDING_ID)
                .with(csrf())
                .contentType("application/json")
                .content("""{ "participantName": "민지", "guestPassword": "12a4" }"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("COMMON_001"))

        verifyNoInteractions(guestContributionService)
    }

    @Test
    fun `전용 접근 토큰 헤더로 비회원 보탬을 수정한다`() {
        val request = UpdateGuestContributionRequest("민지", "수정한 메시지", "BLACK_CONFETTI")
        given(guestContributionService.update(FUNDING_ID, ACCESS_TOKEN, request)).willReturn(updateResponse())

        mockMvc.perform(
            patch("/api/fundings/{fundingId}/contributions/guest", FUNDING_ID)
                .with(csrf())
                .header(GuestContributionAccessTokenProvider.HEADER_NAME, ACCESS_TOKEN)
                .contentType("application/json")
                .content(
                    """
                    {
                      "participantName": "민지",
                      "message": "수정한 메시지",
                      "paperTemplateCode": "BLACK_CONFETTI"
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.message").value("수정한 메시지"))
            .andExpect(jsonPath("$.data.paperTemplateCode").value("BLACK_CONFETTI"))
    }

    @Test
    fun `접근 토큰 헤더가 없으면 수정 요청을 거부한다`() {
        mockMvc.perform(
            patch("/api/fundings/{fundingId}/contributions/guest", FUNDING_ID)
                .with(csrf())
                .contentType("application/json")
                .content(
                    """{ "participantName": "민지", "message": "메시지", "paperTemplateCode": "BLUE_DOT_GRID" }""",
                ),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("FUNDING_033"))

        verifyNoInteractions(guestContributionService)
    }

    @Test
    fun `전용 접근 토큰 헤더로 비회원 보탬을 삭제한다`() {
        mockMvc.perform(
            delete("/api/fundings/{fundingId}/contributions/guest", FUNDING_ID)
                .with(csrf())
                .header(GuestContributionAccessTokenProvider.HEADER_NAME, ACCESS_TOKEN),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").doesNotExist())

        verify(guestContributionService).cancel(FUNDING_ID, ACCESS_TOKEN)
    }

    private fun accessResponse() = GuestContributionResponse(
        contributionId = CONTRIBUTION_ID,
        participantName = "민지",
        amount = 10_000L,
        status = ContributionStatus.AWAITING_TRANSFER,
        message = "생일 축하해!",
        paperTemplateCode = "BLUE_DOT_GRID",
        accessToken = ACCESS_TOKEN,
        accessTokenExpiresAt = Instant.parse("2026-11-18T03:10:00Z"),
    )

    private fun updateResponse() = UpdatedGuestContributionResponse(
        contributionId = CONTRIBUTION_ID,
        participantName = "민지",
        amount = 10_000L,
        status = ContributionStatus.AWAITING_TRANSFER,
        message = "수정한 메시지",
        paperTemplateCode = "BLACK_CONFETTI",
    )

    companion object {
        private const val FUNDING_ID = 12L
        private const val CONTRIBUTION_ID = 31L
        private const val ACCESS_TOKEN = "guest-access-token"
    }
}
