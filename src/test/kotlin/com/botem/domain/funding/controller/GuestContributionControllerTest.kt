package com.botem.domain.funding.controller

import org.junit.jupiter.api.BeforeEach
import com.botem.domain.funding.service.FundingShareKeyResolver
import com.botem.domain.funding.dto.request.AccessGuestContributionRequest
import com.botem.domain.funding.dto.request.UpdateGuestContributionRequest
import com.botem.domain.funding.dto.response.GuestContributionResponse
import com.botem.domain.funding.dto.response.UpdatedGuestContributionResponse
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.service.GuestContributionAccessTokenProvider
import com.botem.domain.funding.service.GuestContributionService
import com.botem.domain.user.repository.UserRepository
import com.botem.domain.user.service.UserStatusCache
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

    private lateinit var fundingShareKeyResolver: FundingShareKeyResolver


    @MockitoBean
    private lateinit var guestContributionService: GuestContributionService

    @MockitoBean
    private lateinit var jwtProvider: JwtProvider

    @MockitoBean
    private lateinit var jwtAuthenticationEntryPoint: JwtAuthenticationEntryPoint

    @MockitoBean
    private lateinit var userRepository: UserRepository

    @MockitoBean
    private lateinit var userStatusCache: UserStatusCache

    @BeforeEach

    fun stubShareKey() {

        given(fundingShareKeyResolver.fundingIdOf(SHARE_KEY)).willReturn(FUNDING_ID)

    }


    @Test
    fun `로그인 없이 이름과 비밀번호로 비회원 보탬을 조회한다`() {
        val request = AccessGuestContributionRequest("민지", "1234")
        given(guestContributionService.access(FUNDING_ID, request)).willReturn(accessResponse())

        mockMvc.perform(
            post("/api/fundings/share/{shareKey}/contributions/guest/access", SHARE_KEY)
                .with(csrf())
                .contentType("application/json")
                .content("""{ "participantName": "민지", "guestPassword": "1234" }"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.contributionId").value(CONTRIBUTION_ID))
            .andExpect(jsonPath("$.data.accessToken").value(ACCESS_TOKEN))
            .andExpect(jsonPath("$.data.message").value("생일 축하해!"))
            .andExpect(jsonPath("$.data.decorationCode").value("PARTY_POPPER"))
            .andExpect(jsonPath("$.data.photoUrl").value(PHOTO_URL))
    }

    @Test
    fun `비밀번호가 숫자 4자리가 아니면 조회하지 않는다`() {
        mockMvc.perform(
            post("/api/fundings/share/{shareKey}/contributions/guest/access", SHARE_KEY)
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
        val request = UpdateGuestContributionRequest(
            participantName = "민지",
            message = "수정한 메시지",
            paperTemplateCode = "BLACK_CONFETTI",
            voiceUrl = VOICE_URL,
            voiceDurationSeconds = 12,
        )
        given(guestContributionService.update(FUNDING_ID, ACCESS_TOKEN, request)).willReturn(updateResponse())

        mockMvc.perform(
            patch("/api/fundings/share/{shareKey}/contributions/guest", SHARE_KEY)
                .with(csrf())
                .header(GuestContributionAccessTokenProvider.HEADER_NAME, ACCESS_TOKEN)
                .contentType("application/json")
                .content(
                    """
                    {
                      "participantName": "민지",
                      "message": "수정한 메시지",
                      "paperTemplateCode": "BLACK_CONFETTI",
                      "voiceUrl": "$VOICE_URL",
                      "voiceDurationSeconds": 12
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.message").value("수정한 메시지"))
            .andExpect(jsonPath("$.data.paperTemplateCode").value("BLACK_CONFETTI"))
            .andExpect(jsonPath("$.data.voiceUrl").value(VOICE_URL))
            .andExpect(jsonPath("$.data.voiceDurationSeconds").value(12))
    }

    @Test
    fun `접근 토큰 헤더가 없으면 수정 요청을 거부한다`() {
        mockMvc.perform(
            patch("/api/fundings/share/{shareKey}/contributions/guest", SHARE_KEY)
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
            delete("/api/fundings/share/{shareKey}/contributions/guest", SHARE_KEY)
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
        decorationCode = "PARTY_POPPER",
        photoUrl = PHOTO_URL,
        voiceUrl = null,
        voiceDurationSeconds = null,
        voiceTranscript = null,
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
        decorationCode = null,
        photoUrl = null,
        voiceUrl = VOICE_URL,
        voiceDurationSeconds = 12,
        voiceTranscript = "생일 축하해",
    )

    companion object {
        private const val SHARE_KEY = "0123456789abcdef0123456789abcdef"
        private const val FUNDING_ID = 12L
        private const val CONTRIBUTION_ID = 31L
        private const val ACCESS_TOKEN = "guest-access-token"
        private const val PHOTO_URL =
            "https://botem-test.s3.ap-northeast-2.amazonaws.com/rolling-paper/12/photo/3f2b8c1e-7a4d-4e9b-9c2a-1d5e6f7a8b9c.jpg"
        private const val VOICE_URL =
            "https://botem-test.s3.ap-northeast-2.amazonaws.com/rolling-paper/12/voice/8a7b6c5d-4e3f-2a1b-9c8d-7e6f5a4b3c2d.m4a"
    }
}
