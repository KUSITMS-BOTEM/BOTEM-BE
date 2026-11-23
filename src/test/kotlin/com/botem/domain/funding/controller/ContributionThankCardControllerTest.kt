package com.botem.domain.funding.controller

import com.botem.domain.funding.dto.request.SendContributionThankCardRequest
import com.botem.domain.funding.dto.response.ContributionThankCardDetailResponse
import com.botem.domain.funding.dto.response.ContributionThankCardResponse
import com.botem.domain.funding.dto.response.ContributionThankCardSenderRole
import com.botem.domain.funding.dto.response.RollingPaperMessageType
import com.botem.domain.funding.dto.response.SurpriseThankCardFriendResponse
import com.botem.domain.funding.dto.response.SurpriseThankCardFriendsResponse
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.entity.ThankCardColor
import com.botem.domain.funding.service.ContributionThankCardService
import org.hamcrest.Matchers.nullValue
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
import java.time.LocalDate
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
        // 숫자는 enum 순서로 바뀌지 않고 거부한다
        listOf("\"GOLD\"", "0").forEach { paperColor ->
        mockMvc.perform(
            post(
                "/api/fundings/{fundingId}/contributions/{contributionId}/thank-card",
                FUNDING_ID,
                CONTRIBUTION_ID,
            )
                .with(authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList())))
                .with(csrf())
                .contentType("application/json")
                .content("""{ "message": "고마워", "paperColor": $paperColor }"""),
        )
            .andExpect(status().isBadRequest)
        }

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
    fun `서프라이즈 수신자가 고마운 친구 목록을 조회한다`() {
        given(contributionThankCardService.getSurpriseThankCardFriends(USER_ID, FUNDING_ID)).willReturn(
            SurpriseThankCardFriendsResponse(
                FUNDING_ID,
                listOf(
                    SurpriseThankCardFriendResponse(
                        contributionId = CONTRIBUTION_ID,
                        name = "민지",
                        profileImageUrl = "https://image.example.com/profile.jpg",
                        organizer = true,
                        messageTypes = listOf(RollingPaperMessageType.TEXT, RollingPaperMessageType.PHOTO),
                        contributedDate = LocalDate.of(2026, 11, 19),
                        thankCardSent = false,
                    ),
                    SurpriseThankCardFriendResponse(
                        contributionId = CONTRIBUTION_ID + 1,
                        name = "익명의 친구",
                        profileImageUrl = null,
                        organizer = false,
                        messageTypes = emptyList(),
                        contributedDate = LocalDate.of(2026, 11, 20),
                        thankCardSent = false,
                    ),
                ),
            ),
        )

        mockMvc.perform(
            get("/api/fundings/{fundingId}/surprise/thank-card-friends", FUNDING_ID)
                .with(authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList()))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.friends[0].name").value("민지"))
            .andExpect(jsonPath("$.data.friends[0].profileImageUrl").value("https://image.example.com/profile.jpg"))
            .andExpect(jsonPath("$.data.friends[0].organizer").value(true))
            .andExpect(jsonPath("$.data.friends[0].messageTypes[1]").value("PHOTO"))
            .andExpect(jsonPath("$.data.friends[0].contributedDate").value("2026-11-19"))
            .andExpect(jsonPath("$.data.friends[1].profileImageUrl").value(nullValue()))

        verify(contributionThankCardService).getSurpriseThankCardFriends(USER_ID, FUNDING_ID)
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
            senderRole = ContributionThankCardSenderRole.FUNDING_OWNER,
            fundingType = FundingType.BIRTHDAY,
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
