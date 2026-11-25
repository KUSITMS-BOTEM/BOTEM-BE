package com.botem.global.handler

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// 로그인한 사용자의 잘못된 요청이 500(서버 오류)이 아니라 원래 상태 코드로 응답되는지 확인한다
@SpringBootTest
@AutoConfigureMockMvc
class ClientErrorStatusIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    private val loggedIn = authentication(UsernamePasswordAuthenticationToken(1L, null, emptyList()))

    @Test
    fun `없는 주소는 404로 응답한다`() {
        mockMvc.perform(get("/api/not-exists").with(loggedIn))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("COMMON_404"))
    }

    @Test
    fun `지원하지 않는 메서드는 허용 메서드를 알려 주며 405로 응답한다`() {
        mockMvc.perform(delete("/api/home/fundings").with(loggedIn))
            .andExpect(status().isMethodNotAllowed)
            .andExpect(header().string(HttpHeaders.ALLOW, "GET"))
            .andExpect(jsonPath("$.error.code").value("COMMON_405"))
    }

    @Test
    fun `지원하지 않는 요청 형식은 415로 응답한다`() {
        mockMvc.perform(post("/api/wishes").with(loggedIn).contentType(MediaType.TEXT_PLAIN).content("text"))
            .andExpect(status().isUnsupportedMediaType)
            .andExpect(jsonPath("$.error.code").value("COMMON_415"))
    }

    @Test
    fun `비로그인 요청은 지금처럼 인증 단계에서 401로 막는다`() {
        mockMvc.perform(get("/api/not-exists"))
            .andExpect(status().isUnauthorized)
    }
}
