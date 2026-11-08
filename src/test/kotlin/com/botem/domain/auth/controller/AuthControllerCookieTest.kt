package com.botem.domain.auth.controller

import com.botem.domain.auth.service.AuthService
import com.botem.domain.auth.service.IssuedTokens
import com.botem.domain.auth.service.KakaoLoginResult
import com.botem.domain.user.repository.UserRepository
import com.botem.global.config.SecurityConfig
import com.botem.global.jwt.JwtAuthenticationEntryPoint
import com.botem.global.jwt.JwtProvider
import com.botem.global.property.AuthCookieProperties
import com.botem.global.property.JwtProperties
import jakarta.servlet.http.Cookie
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
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
        "auth-cookie.domain=.botem.site",
    ],
)
@Import(SecurityConfig::class, AuthCookieFactory::class)
@EnableConfigurationProperties(JwtProperties::class, AuthCookieProperties::class)
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
    private lateinit var userRepository: UserRepository

    private val tokens = IssuedTokens(accessToken = "new-access", refreshToken = "new-refresh")

    @Test
    fun `로그인하면 Access 토큰은 본문으로 Refresh 토큰은 쿠키로만 전달한다`() {
        given(authService.kakaoLogin("code", null)).willReturn(KakaoLoginResult(tokens, isNewUser = true))

        mockMvc.perform(
            post("/api/auth/kakao/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{ "code": "code" }"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.accessToken").value("new-access"))
            .andExpect(jsonPath("$.data.isNewUser").value(true))
            .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
            .andExpect(
                header().stringValues(
                    HttpHeaders.SET_COOKIE,
                    hasItem(
                        allOf(
                            containsString("refreshToken=new-refresh"),
                            containsString("Path=/api/auth"),
                            containsString("Max-Age=604800"),
                            containsString("Secure"),
                            containsString("HttpOnly"),
                            containsString("SameSite=Lax"),
                            not(containsString("Domain=")),
                        ),
                    ),
                ),
            )
            .andExpect(
                header().stringValues(
                    HttpHeaders.SET_COOKIE,
                    hasItem(
                        allOf(
                            containsString("isLoggedIn=true"),
                            containsString("Domain=.botem.site"),
                            containsString("Path=/"),
                            containsString("Max-Age=604800"),
                            containsString("Secure"),
                            containsString("SameSite=Lax"),
                            not(containsString("HttpOnly")),
                        ),
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
            .andExpect(header().stringValues(HttpHeaders.SET_COOKIE, hasItem(containsString("refreshToken=new-refresh"))))
            .andExpect(header().stringValues(HttpHeaders.SET_COOKIE, hasItem(containsString("isLoggedIn=true"))))
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
            .andExpect(
                header().stringValues(
                    HttpHeaders.SET_COOKIE,
                    hasItem(allOf(containsString("refreshToken="), containsString("Path=/api/auth"), containsString("Max-Age=0"))),
                ),
            )
            .andExpect(
                header().stringValues(
                    HttpHeaders.SET_COOKIE,
                    hasItem(allOf(containsString("isLoggedIn="), containsString("Domain=.botem.site"), containsString("Max-Age=0"))),
                ),
            )

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
