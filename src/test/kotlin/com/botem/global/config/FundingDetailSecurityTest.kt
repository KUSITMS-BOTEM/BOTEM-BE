package com.botem.global.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
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

    @Test
    fun `숫자가 아닌 단일 경로는 공개되지 않는다`() {
        mockMvc.perform(get("/api/fundings/me"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `유효하지 않은 토큰으로 펀딩 상세를 조회하면 비로그인으로 처리하지 않고 401로 응답한다`() {
        mockMvc.perform(
            get("/api/fundings/{fundingId}", MISSING_FUNDING_ID)
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AUTH_002"))
    }

    companion object {
        private const val MISSING_FUNDING_ID = 999_999L
    }
}
