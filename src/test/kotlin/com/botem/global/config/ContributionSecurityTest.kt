package com.botem.global.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class ContributionSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `로그인하지 않아도 금액 보태기 API에 접근할 수 있다`() {
        mockMvc.perform(
            post("/api/fundings/{fundingId}/contributions", MISSING_ID)
                .contentType("application/json")
                .content(CONTRIBUTE_BODY),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("FUNDING_002"))
    }

    @Test
    fun `로그인하지 않아도 송금 완료 표시 API에 접근할 수 있다`() {
        mockMvc.perform(
            post("/api/fundings/{fundingId}/contributions/{contributionId}/transfer-sent", MISSING_ID, MISSING_ID)
                .contentType("application/json")
                .content("""{"guestPassword": "1234"}"""),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("FUNDING_023"))
    }

    @Test
    fun `유효하지 않은 토큰으로 보태면 비로그인으로 처리하지 않고 401로 응답한다`() {
        mockMvc.perform(
            post("/api/fundings/{fundingId}/contributions", MISSING_ID)
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                .contentType("application/json")
                .content(CONTRIBUTE_BODY),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AUTH_002"))
    }

    @Test
    fun `참여 하위의 다른 경로는 공개되지 않는다`() {
        mockMvc.perform(post("/api/fundings/{fundingId}/contributions/{contributionId}/confirm", MISSING_ID, MISSING_ID))
            .andExpect(status().isUnauthorized)
    }

    companion object {
        private const val MISSING_ID = 999_999L
        private const val CONTRIBUTE_BODY = """
            {
              "requestKey": "7f1c2a9e-3b4d-4e5f-8a6b-1c2d3e4f5a6b",
              "participantName": "민지",
              "amount": 10000,
              "message": "생일 축하해!",
              "paperTemplateCode": "BLUE_DOT_GRID",
              "guestPassword": "1234"
            }
        """
    }
}
