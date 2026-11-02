package com.botem.global.config

import com.botem.domain.auth.controller.AuthController
import com.botem.domain.auth.service.AuthService
import com.botem.global.jwt.JwtAuthenticationEntryPoint
import com.botem.global.jwt.JwtProvider
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(AuthController::class)
@Import(SecurityConfig::class)
class SecurityConfigCorsTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var authService: AuthService

    @MockitoBean
    private lateinit var jwtProvider: JwtProvider

    @MockitoBean
    private lateinit var jwtAuthenticationEntryPoint: JwtAuthenticationEntryPoint

    @Test
    fun `허용된 프론트 Origin의 Preflight 요청을 허용한다`() {
        mockMvc.perform(preflight("https://botem-fe.vercel.app"))
            .andExpect(status().isOk)
            .andExpect(header().string("Access-Control-Allow-Origin", "https://botem-fe.vercel.app"))
    }

    @Test
    fun `허용되지 않은 Origin의 Preflight 요청을 거부한다`() {
        mockMvc.perform(preflight("https://evil.example.com"))
            .andExpect(status().isForbidden)
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
    }

    private fun preflight(origin: String) =
        options("/api/auth/kakao/login")
            .header("Origin", origin)
            .header("Access-Control-Request-Method", "POST")
}
