package com.botem.domain.auth.controller

import com.botem.domain.auth.service.AuthService
import com.botem.domain.auth.service.IssuedTokens
import com.botem.global.config.SecurityConfig
import com.botem.global.jwt.JwtAuthenticationEntryPoint
import com.botem.global.jwt.JwtProvider
import com.botem.global.notification.DiscordErrorNotifier
import com.botem.global.property.JwtProperties
import jakarta.servlet.http.Cookie
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
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
class AuthControllerCookieTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var authService: AuthService

    @MockitoBean
    private lateinit var jwtProvider: JwtProvider

    @MockitoBean
    private lateinit var jwtAuthenticationEntryPoint: JwtAuthenticationEntryPoint

    @MockitoBean
    private lateinit var discordErrorNotifier: DiscordErrorNotifier

    private val tokens = IssuedTokens(accessToken = "new-access", refreshToken = "new-refresh")

    @Test
    fun `로그인하면 Access 토큰은 본문으로 Refresh 토큰은 쿠키로만 전달한다`() {
        given(authService.kakaoLogin("code", null)).willReturn(tokens)

        mockMvc.perform(
            post("/api/auth/kakao/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{ "code": "code" }"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.accessToken").value("new-access"))
            .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
            .andExpect(
                header().string(
                    HttpHeaders.SET_COOKIE,
                    allOf(
                        containsString("refreshToken=new-refresh"),
                        containsString("Path=/api/auth"),
                        containsString("Max-Age=604800"),
                        containsString("Secure"),
                        containsString("HttpOnly"),
                        containsString("SameSite=Lax"),
                    ),
                ),
            )
    }

    @Test
    fun `쿠키의 Refresh 토큰으로 재발급한다`() {
        given(authService.reissue("old-refresh")).willReturn(tokens)

        mockMvc.perform(post("/api/auth/reissue").cookie(Cookie("refreshToken", "old-refresh")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.accessToken").value("new-access"))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=new-refresh")))
    }

    @Test
    fun `쿠키 없이 재발급하면 401을 반환한다`() {
        mockMvc.perform(post("/api/auth/reissue"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AUTH_002"))
    }

    @Test
    fun `로그아웃하면 토큰을 삭제하고 쿠키를 만료시킨다`() {
        mockMvc.perform(post("/api/auth/logout").cookie(Cookie("refreshToken", "old-refresh")))
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/auth")))

        verify(authService).logout("old-refresh")
    }

    @Test
    fun `쿠키 없이 로그아웃해도 성공한다`() {
        mockMvc.perform(post("/api/auth/logout"))
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))

        verifyNoInteractions(authService)
    }
}
