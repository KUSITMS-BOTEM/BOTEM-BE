package com.botem.domain.funding.controller

import com.botem.domain.funding.dto.request.SendContributionThankCardRequest
import com.botem.domain.funding.dto.response.ContributionThankCardDetailResponse
import com.botem.domain.funding.dto.response.ContributionThankCardResponse
import com.botem.domain.funding.entity.ThankCardColor
import com.botem.domain.funding.service.ContributionThankCardService
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

@WebMvcTest(controllers = [ContributionThankCardController::class])
class ContributionThankCardControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var contributionThankCardService: ContributionThankCardService

    @Test
    fun `개설자가 확정 보탬에 감사카드를 보낸다`() {
        val request = SendContributionThankCardRequest("정말 고마워!", ThankCardColor.PINK)
        given(contributionThankCardService.send(USER_ID, FUNDING_ID, CONTRIBUTION_ID, request)).willReturn(
            ContributionThankCardResponse(THANK_CARD_ID, CONTRIBUTION_ID, request.message, ThankCardColor.PINK, NOW, SHARE_URL),
        )

        mockMvc.perform(
            post(
                "/api/fundings/{fundingId}/contributions/{contributionId}/thank-card",
                FUNDING_ID,
                CONTRIBUTION_ID,
            )
                .with(authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList())))
                .with(csrf())
                .contentType("application/json")
                .content("""{ "message": "정말 고마워!", "paperColor": "PINK" }"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.thankCardId").value(THANK_CARD_ID))
            .andExpect(jsonPath("$.data.message").value("정말 고마워!"))
            .andExpect(jsonPath("$.data.paperColor").value("PINK"))
            .andExpect(jsonPath("$.data.shareUrl").value(SHARE_URL))

        verify(contributionThankCardService).send(USER_ID, FUNDING_ID, CONTRIBUTION_ID, request)
    }

    @Test
    fun `지원하지 않는 편지지 색상이면 400을 반환한다`() {
        mockMvc.perform(
            post(
                "/api/fundings/{fundingId}/contributions/{contributionId}/thank-card",
                FUNDING_ID,
                CONTRIBUTION_ID,
            )
                .with(authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList())))
                .with(csrf())
                .contentType("application/json")
                .content("""{ "message": "고마워", "paperColor": "GOLD" }"""),
        )
            .andExpect(status().isBadRequest)

        verifyNoInteractions(contributionThankCardService)
    }

    @Test
    fun `로그인 참여자가 자신이 받은 감사카드를 조회한다`() {
        given(contributionThankCardService.getForParticipant(USER_ID, CONTRIBUTION_ID)).willReturn(detailResponse())

        mockMvc.perform(
            get("/api/contributions/{contributionId}/thank-card", CONTRIBUTION_ID)
                .with(authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList()))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.senderName").value("지유"))
            .andExpect(jsonPath("$.data.recipientName").value("하린"))
            .andExpect(jsonPath("$.data.amount").doesNotExist())
    }

    @Test
    fun `로그인하지 않고 공유 키로 감사카드를 조회한다`() {
        given(contributionThankCardService.getByShareKey(SHARE_KEY)).willReturn(detailResponse())

        mockMvc.perform(
            get("/api/thank-cards/{shareKey}", SHARE_KEY)
                .with(authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList()))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.productName").value("무선 헤드폰"))
            .andExpect(jsonPath("$.data.message").value("정말 고마워!"))
            .andExpect(jsonPath("$.data.amount").doesNotExist())
    }

    private fun detailResponse(): ContributionThankCardDetailResponse =
        ContributionThankCardDetailResponse(
            thankCardId = THANK_CARD_ID,
            fundingId = FUNDING_ID,
            contributionId = CONTRIBUTION_ID,
            senderName = "지유",
            recipientName = "하린",
            productName = "무선 헤드폰",
            productImageUrl = "https://image.example.com/product.jpg",
            message = "정말 고마워!",
            paperColor = ThankCardColor.LIME,
            sentAt = NOW,
            shareUrl = SHARE_URL,
        )

    companion object {
        private const val USER_ID = 1L
        private const val FUNDING_ID = 10L
        private const val CONTRIBUTION_ID = 20L
        private const val THANK_CARD_ID = 30L
        private const val SHARE_KEY = "550e8400e29b41d4a716446655440000"
        private const val SHARE_URL = "https://botem.me/thank-cards/$SHARE_KEY"
        private val NOW = LocalDateTime.of(2026, 11, 19, 20, 0)
    }
}
