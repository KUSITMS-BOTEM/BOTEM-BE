package com.botem.global.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
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
