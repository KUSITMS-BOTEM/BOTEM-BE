package com.botem.global.config

import com.botem.domain.auth.controller.AuthController
import com.botem.domain.auth.controller.AuthCookieFactory
import com.botem.domain.auth.service.AuthService
import com.botem.domain.user.repository.UserRepository
import com.botem.global.jwt.JwtAuthenticationEntryPoint
import com.botem.global.jwt.JwtProvider
import com.botem.global.property.JwtProperties
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(
    controllers = [AuthController::class],
    properties = [
        "jwt.secret=test-secret",
        "jwt.access-token-expiration=60m",
        "jwt.refresh-token-expiration=7d",
    ],
)
@Import(SecurityConfig::class)
@EnableConfigurationProperties(JwtProperties::class)
class SecurityConfigCorsTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var authService: AuthService

    @MockitoBean
    private lateinit var authCookieFactory: AuthCookieFactory

    @MockitoBean
    private lateinit var jwtProvider: JwtProvider

    @MockitoBean
    private lateinit var jwtAuthenticationEntryPoint: JwtAuthenticationEntryPoint

    @MockitoBean
    private lateinit var userRepository: UserRepository

    @Test
    fun `허용된 프론트 Origin의 Preflight 요청을 허용한다`() {
        listOf("https://botem.site", "https://botem-fe.vercel.app", "http://localhost:3000").forEach { origin ->
            mockMvc.perform(preflight(origin))
                .andExpect(status().isOk)
                .andExpect(header().string("Access-Control-Allow-Origin", origin))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
        }
    }

    @Test
    fun `허용되지 않은 Origin의 Preflight 요청을 거부한다`() {
        mockMvc.perform(preflight("https://evil.example.com"))
            .andExpect(status().isForbidden)
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
    }

    @Test
    fun `Sentry 추적 헤더를 포함한 Preflight 요청을 허용한다`() {
        mockMvc.perform(
            preflight("https://botem.site")
                .header("Access-Control-Request-Headers", "content-type,sentry-trace,baggage"),
        )
            .andExpect(status().isOk)
            .andExpect(header().string("Access-Control-Allow-Headers", "content-type, sentry-trace, baggage"))
    }

    private fun preflight(origin: String) =
        options("/api/auth/kakao/login")
            .header("Origin", origin)
            .header("Access-Control-Request-Method", "POST")
}
