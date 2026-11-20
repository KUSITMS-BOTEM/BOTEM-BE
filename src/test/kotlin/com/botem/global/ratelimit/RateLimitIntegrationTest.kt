package com.botem.global.ratelimit

import com.botem.global.logging.RequestIdFilter
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["rate-limit.guest-access-per-minute=2"])
class RateLimitIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `한도를 넘은 비회원 확인 요청은 본문 처리 전에 429로 끊고 브라우저가 읽을 수 있게 CORS 헤더를 붙인다`() {
        repeat(2) {
            mockMvc.perform(guestAccess()).andExpect(status().isNotFound)
        }

        mockMvc.perform(guestAccess())
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.error.code").value("COMMON_429"))
            .andExpect(header().exists("Retry-After"))
            .andExpect(header().exists(RequestIdFilter.HEADER_NAME))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND_ORIGIN))
    }

    private fun guestAccess() =
        post("/api/fundings/share/{shareKey}/contributions/guest/access", MISSING_SHARE_KEY)
            .header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
            .contentType("application/json")
            .content("""{ "participantName": "민지", "guestPassword": "1234" }""")

    companion object {
        private const val MISSING_SHARE_KEY = "ffffffffffffffffffffffffffffffff"
        private const val FRONTEND_ORIGIN = "http://localhost:3000"
    }
}
