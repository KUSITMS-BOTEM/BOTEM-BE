package com.botem.global.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class FundingDetailSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `로그인하지 않아도 펀딩 상세 API에 접근할 수 있다`() {
        mockMvc.perform(get("/api/fundings/{fundingId}", MISSING_FUNDING_ID))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("FUNDING_002"))
    }

    @Test
    fun `펀딩 상세 하위 경로는 여전히 로그인이 필요하다`() {
        mockMvc.perform(get("/api/fundings/{fundingId}/result", MISSING_FUNDING_ID))
            .andExpect(status().isUnauthorized)
    }

    companion object {
        private const val MISSING_FUNDING_ID = 999_999L
    }
}
