package com.botem.domain.funding.controller

import com.botem.domain.account.entity.Bank
import com.botem.domain.funding.dto.request.CreateContributionRequest
import com.botem.domain.funding.dto.request.CreateMessageContributionRequest
import com.botem.domain.funding.dto.request.MarkTransferSentRequest
import com.botem.domain.funding.dto.response.ContributionPayoutAccountResponse
import com.botem.domain.funding.dto.response.ContributionResponse
import com.botem.domain.funding.dto.response.MessageContributionResponse
import com.botem.domain.funding.dto.response.TransferSentResponse
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.service.ContributionService
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [ContributionController::class])
class ContributionControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var contributionService: ContributionService

    @Test
    fun `로그인 사용자가 금액을 보태면 201과 받을 계좌를 반환한다`() {
        val request = CreateContributionRequest(
            requestKey = REQUEST_KEY,
            amount = 10_000L,
            message = "생일 축하해!",
            paperTemplateCode = "BLUE_DOT_GRID",
        )
        given(contributionService.contribute(USER_ID, FUNDING_ID, request)).willReturn(
            ContributionResponse(
                contributionId = CONTRIBUTION_ID,
                participantName = "지유친구",
                amount = 10_000L,
                status = ContributionStatus.AWAITING_TRANSFER,
                payoutAccount = ContributionPayoutAccountResponse(Bank.TOSS_BANK, "토스뱅크", "100012345678", "김지유"),
            ),
        )

        mockMvc.perform(
            post("/api/fundings/{fundingId}/contributions", FUNDING_ID)
                .with(loggedIn())
                .with(csrf())
                .contentType("application/json")
                .content(
                    """
                    {
                      "requestKey": "$REQUEST_KEY",
                      "amount": 10000,
                      "message": "생일 축하해!",
                      "paperTemplateCode": "BLUE_DOT_GRID"
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.contributionId").value(CONTRIBUTION_ID))
            .andExpect(jsonPath("$.data.status").value("AWAITING_TRANSFER"))
            .andExpect(jsonPath("$.data.payoutAccount.bankName").value("토스뱅크"))
            .andExpect(jsonPath("$.data.payoutAccount.accountNumber").value("100012345678"))
    }

    @Test
    fun `메시지가 460자를 넘거나 비밀번호가 숫자 4자리가 아니면 400을 반환한다`() {
        listOf(
            body(message = "가".repeat(461)),
            body(guestPassword = "12a4"),
            body(message = " "),
            """{"requestKey": "$REQUEST_KEY", "amount": 10000, "paperTemplateCode": "BLUE_DOT_GRID"}""",
            body(amount = 0L),
        ).forEach { content ->
            mockMvc.perform(
                post("/api/fundings/{fundingId}/contributions", FUNDING_ID)
                    .with(loggedIn())
                    .with(csrf())
                    .contentType("application/json")
                    .content(content),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("COMMON_001"))
        }

        verifyNoInteractions(contributionService)
    }

    @Test
    fun `금액에 소수를 보내면 잘라서 받지 않고 400을 반환한다`() {
        mockMvc.perform(
            post("/api/fundings/{fundingId}/contributions", FUNDING_ID)
                .with(loggedIn())
                .with(csrf())
                .contentType("application/json")
                .content(body().replace("\"amount\": 10000", "\"amount\": 10000.9")),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("COMMON_001"))

        verifyNoInteractions(contributionService)
    }

    @Test
    fun `메시지로 보태면 201과 메시지 전용 상태를 반환한다`() {
        val request = CreateMessageContributionRequest(
            requestKey = REQUEST_KEY,
            message = "생일 축하해!",
            paperTemplateCode = "BLUE_DOT_GRID",
        )
        given(contributionService.leaveMessage(USER_ID, FUNDING_ID, request)).willReturn(
            MessageContributionResponse(CONTRIBUTION_ID, "지유친구", ContributionStatus.MESSAGE_ONLY),
        )

        mockMvc.perform(
            post("/api/fundings/{fundingId}/contributions/message-only", FUNDING_ID)
                .with(loggedIn())
                .with(csrf())
                .contentType("application/json")
                .content(
                    """{"requestKey": "$REQUEST_KEY", "message": "생일 축하해!", "paperTemplateCode": "BLUE_DOT_GRID"}""",
                ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.contributionId").value(CONTRIBUTION_ID))
            .andExpect(jsonPath("$.data.status").value("MESSAGE_ONLY"))
    }

    @Test
    fun `메시지로 보탤 때 메시지가 460자를 넘으면 400을 반환한다`() {
        mockMvc.perform(
            post("/api/fundings/{fundingId}/contributions/message-only", FUNDING_ID)
                .with(loggedIn())
                .with(csrf())
                .contentType("application/json")
                .content(
                    """{"requestKey": "$REQUEST_KEY", "message": "${"가".repeat(461)}", "paperTemplateCode": "BLUE_DOT_GRID"}""",
                ),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("COMMON_001"))

        verifyNoInteractions(contributionService)
    }

    @Test
    fun `송금 완료를 표시하면 확인 대기 상태를 반환한다`() {
        given(
            contributionService.markTransferSent(USER_ID, FUNDING_ID, CONTRIBUTION_ID, MarkTransferSentRequest()),
        ).willReturn(TransferSentResponse(CONTRIBUTION_ID, ContributionStatus.PENDING_CONFIRMATION))

        mockMvc.perform(
            post("/api/fundings/{fundingId}/contributions/{contributionId}/transfer-sent", FUNDING_ID, CONTRIBUTION_ID)
                .with(loggedIn())
                .with(csrf())
                .contentType("application/json")
                .content("{}"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("PENDING_CONFIRMATION"))
    }

    @Test
    fun `송금 완료 비밀번호가 숫자 4자리가 아니면 400을 반환한다`() {
        mockMvc.perform(
            post("/api/fundings/{fundingId}/contributions/{contributionId}/transfer-sent", FUNDING_ID, CONTRIBUTION_ID)
                .with(loggedIn())
                .with(csrf())
                .contentType("application/json")
                .content("""{"guestPassword": "12345"}"""),
        )
            .andExpect(status().isBadRequest)

        verifyNoInteractions(contributionService)
    }

    private fun loggedIn() = authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList()))

    private fun body(
        amount: Long = 10_000L,
        message: String = "생일 축하해!",
        guestPassword: String = "1234",
    ): String =
        """
        {
          "requestKey": "$REQUEST_KEY",
          "participantName": "민지",
          "amount": $amount,
          "message": "$message",
          "paperTemplateCode": "BLUE_DOT_GRID",
          "guestPassword": "$guestPassword"
        }
        """.trimIndent()

    companion object {
        private const val USER_ID = 1L
        private const val FUNDING_ID = 10L
        private const val CONTRIBUTION_ID = 31L
        private const val REQUEST_KEY = "7f1c2a9e-3b4d-4e5f-8a6b-1c2d3e4f5a6b"
    }
}
