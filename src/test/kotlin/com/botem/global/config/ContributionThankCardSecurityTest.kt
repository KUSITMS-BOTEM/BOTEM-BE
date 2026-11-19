package com.botem.global.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class ContributionThankCardSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `로그인하지 않아도 공유 감사카드를 조회할 수 있다`() {
        mockMvc.perform(get("/api/thank-cards/{shareKey}", MISSING_SHARE_KEY))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("FUNDING_050"))
    }

    @Test
    fun `참여자 본인 조회는 로그인이 필요하다`() {
        mockMvc.perform(get("/api/contributions/{contributionId}/thank-card", MISSING_ID))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `감사카드 전송은 로그인이 필요하다`() {
        mockMvc.perform(
            post("/api/fundings/{fundingId}/contributions/{contributionId}/thank-card", MISSING_ID, MISSING_ID)
                .contentType("application/json")
                .content("""{ "message": "고마워" }"""),
        )
            .andExpect(status().isUnauthorized)
    }

    companion object {
        private const val MISSING_ID = 999_999L
        private const val MISSING_SHARE_KEY = "00000000000000000000000000000000"
    }
}
