package com.botem.domain.funding.controller

import com.botem.domain.funding.dto.request.SendSurpriseThankCardRequest
import com.botem.domain.funding.dto.response.RollingPaperMessageResponse
import com.botem.domain.funding.dto.response.SurpriseRevealDetailResponse
import com.botem.domain.funding.dto.response.SurpriseThankCardResponse
import com.botem.domain.funding.service.SurpriseRevealService
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
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

@WebMvcTest(controllers = [SurpriseRevealController::class])
class SurpriseRevealControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var surpriseRevealService: SurpriseRevealService

    @Test
    fun `수신자가 서프라이즈 공개 상세를 조회한다`() {
        given(surpriseRevealService.getDetail(USER_ID, FUNDING_ID)).willReturn(detailResponse(openedAt = null))

        mockMvc.perform(
            get("/api/fundings/{fundingId}/surprise", FUNDING_ID)
                .with(authentication()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.fundingId").value(FUNDING_ID))
            .andExpect(jsonPath("$.data.recipientName").value("지유"))
            .andExpect(jsonPath("$.data.confirmedAmount").value(429000))
            .andExpect(jsonPath("$.data.participantCount").value(8))
            .andExpect(jsonPath("$.data.messageCount").value(1))
            .andExpect(jsonPath("$.data.messages[0].participantName").value("하은"))

        verify(surpriseRevealService).getDetail(USER_ID, FUNDING_ID)
    }

    @Test
    fun `수신자가 서프라이즈를 연다`() {
        given(surpriseRevealService.open(USER_ID, FUNDING_ID)).willReturn(detailResponse(OPENED_AT))

        mockMvc.perform(
            post("/api/fundings/{fundingId}/surprise/open", FUNDING_ID)
                .with(authentication())
                .with(csrf()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.openedAt").value("2026-11-15T09:41:00"))

        verify(surpriseRevealService).open(USER_ID, FUNDING_ID)
    }

    @Test
    fun `수신자가 감사 카드를 전송한다`() {
        val request = SendSurpriseThankCardRequest("정말 고마워!")
        given(surpriseRevealService.sendThankCard(USER_ID, FUNDING_ID, request)).willReturn(
            SurpriseThankCardResponse(
                thankCardId = 7L,
                fundingId = FUNDING_ID,
                message = request.message,
                sentAt = OPENED_AT.plusMinutes(1),
            ),
        )

        mockMvc.perform(
            post("/api/fundings/{fundingId}/surprise/thank-card", FUNDING_ID)
                .with(authentication())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"message":"정말 고마워!"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.thankCardId").value(7))
            .andExpect(jsonPath("$.data.message").value("정말 고마워!"))
            .andExpect(jsonPath("$.data.sentAt").value("2026-11-15T09:42:00"))

        verify(surpriseRevealService).sendThankCard(USER_ID, FUNDING_ID, request)
    }

    @Test
    fun `빈 감사 메시지는 요청 검증에서 거부한다`() {
        mockMvc.perform(
            post("/api/fundings/{fundingId}/surprise/thank-card", FUNDING_ID)
                .with(authentication())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"message":" "}"""),
        )
            .andExpect(status().isBadRequest)

        verifyNoInteractions(surpriseRevealService)
    }

    @Test
    fun `인증 없이 서프라이즈 상세을 조회하면 401을 반환한다`() {
        mockMvc.perform(get("/api/fundings/{fundingId}/surprise", FUNDING_ID))
            .andExpect(status().isUnauthorized)

        verifyNoInteractions(surpriseRevealService)
    }

    private fun authentication() =
        authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList()))

    private fun detailResponse(openedAt: LocalDateTime?): SurpriseRevealDetailResponse =
        SurpriseRevealDetailResponse(
            fundingId = FUNDING_ID,
            recipientName = "지유",
            organizerName = "민지",
            productName = "무선 헤드폰",
            productImageUrl = "https://image.example.com/product.jpg",
            confirmedAmount = 429_000L,
            participantCount = 8L,
            messageCount = 1,
            revealAt = OPENED_AT.minusHours(1),
            openedAt = openedAt,
            thankCardSent = false,
            messages = listOf(
                RollingPaperMessageResponse(
                    messageId = 1L,
                    participantName = "하은",
                    participantProfileImageUrl = "https://image.example.com/profile.jpg",
                    content = "생일 축하해!",
                    paperTemplateCode = "BLUE_DOT_GRID",
                    decorationCode = null,
                    photoUrl = null,
                    voiceUrl = null,
                    voiceDurationSeconds = null,
                    voiceTranscript = null,
                ),
            ),
        )

    companion object {
        private const val USER_ID = 2L
        private const val FUNDING_ID = 12L
        private val OPENED_AT = LocalDateTime.of(2026, 11, 15, 9, 41)
    }
}
