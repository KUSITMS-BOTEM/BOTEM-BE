package com.botem.global.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.Base64

@SpringBootTest(
    properties = [
        "swagger.username=swagger-user",
        "swagger.password=swagger-password",
    ],
)
@AutoConfigureMockMvc
class SwaggerSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `인증 정보 없이 API 문서에 접근하면 401로 응답한다`() {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `잘못된 비밀번호로 API 문서에 접근하면 401로 응답한다`() {
        mockMvc.perform(get("/v3/api-docs").header("Authorization", basic("swagger-user", "wrong")))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `올바른 계정으로 API 문서에 접근할 수 있다`() {
        mockMvc.perform(get("/v3/api-docs").header("Authorization", basic("swagger-user", "swagger-password")))
            .andExpect(status().isOk)
    }

    @Test
    fun `인증 정보 없이 API 연동 현황 보드에 접근하면 401로 응답한다`() {
        mockMvc.perform(get("/api-board"))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api-board/statuses"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `Swagger 계정으로 보드를 열고 연동 상태를 저장한다`() {
        val auth = basic("swagger-user", "swagger-password")

        mockMvc.perform(get("/api-board").header("Authorization", auth))
            .andExpect(status().isOk)
            .andExpect(forwardedUrl("/api-board/index.html"))
        mockMvc.perform(
            put("/api-board/statuses")
                .header("Authorization", auth)
                .contentType("application/json")
                .content("""{ "method": "GET", "path": "/api/board-test", "owner": "정푸름", "integrated": true }"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.integrated").value(true))
        mockMvc.perform(get("/api-board/statuses").header("Authorization", auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[?(@.path == '/api/board-test')].owner").value("정푸름"))
        mockMvc.perform(get("/v3/api-docs").header("Authorization", auth))
            .andExpect(jsonPath("$.paths['/api-board/statuses']").doesNotExist())
    }

    private fun basic(username: String, password: String): String =
        "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray())
}

@SpringBootTest
@AutoConfigureMockMvc
class SwaggerSecurityDisabledTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `Swagger 계정이 없으면 인증 없이 API 문서에 접근할 수 있다`() {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
    }
}
