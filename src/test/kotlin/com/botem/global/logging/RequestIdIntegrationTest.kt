package com.botem.global.logging

import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class RequestIdIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `인증에 실패한 요청에도 요청 ID를 붙이고 프론트가 읽을 수 있게 노출한다`() {
        mockMvc.perform(
            get("/api/fundings/{fundingId}/management", 1L)
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(RequestIdFilter.HEADER_NAME, "front-1234"),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(header().string(RequestIdFilter.HEADER_NAME, "front-1234"))
            .andExpect(
                header().string(
                    HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                    containsString(RequestIdFilter.HEADER_NAME),
                ),
            )
    }
}
