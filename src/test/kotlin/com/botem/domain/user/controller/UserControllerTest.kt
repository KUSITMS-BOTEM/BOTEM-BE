package com.botem.domain.user.controller

import com.botem.domain.auth.controller.AuthCookieFactory
import com.botem.domain.user.dto.request.UpdateMyProfileRequest
import com.botem.domain.user.dto.response.MyProfileResponse
import com.botem.domain.user.service.OnboardingService
import com.botem.domain.user.service.UserService
import com.botem.domain.user.service.UserWithdrawalService
import com.botem.global.property.AuthCookieProperties
import com.botem.global.property.JwtProperties
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.hasItem
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.MonthDay

@WebMvcTest(
    controllers = [UserController::class],
    properties = [
        "jwt.secret=test-secret",
        "jwt.access-token-expiration=60m",
        "jwt.refresh-token-expiration=7d",
    ],
)
@Import(AuthCookieFactory::class)
@EnableConfigurationProperties(JwtProperties::class, AuthCookieProperties::class)
class UserControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var userService: UserService

    @MockitoBean
    private lateinit var userWithdrawalService: UserWithdrawalService

    @MockitoBean
    private lateinit var onboardingService: OnboardingService

    @Test
    fun `회원 탈퇴에 성공하면 Refresh Token 쿠키와 로그인 상태 쿠키를 만료시킨다`() {
        val authentication = UsernamePasswordAuthenticationToken(USER_ID, null, emptyList())

        mockMvc.perform(
            delete("/api/users/me")
                .with(authentication(authentication))
                .with(csrf()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isEmpty)
            .andExpect(
                header().stringValues(
                    HttpHeaders.SET_COOKIE,
                    hasItem(
                        allOf(
                            containsString("refreshToken="),
                            containsString("Path=/api/auth"),
                            containsString("Max-Age=0"),
                            containsString("Secure"),
                            containsString("HttpOnly"),
                            containsString("SameSite=Lax"),
                        ),
                    ),
                ),
            )
            .andExpect(
                header().stringValues(
                    HttpHeaders.SET_COOKIE,
                    hasItem(allOf(containsString("isLoggedIn="), containsString("Path=/"), containsString("Max-Age=0"))),
                ),
            )

        verify(userWithdrawalService).withdraw(USER_ID)
    }

    @Test
    fun `생일은 연도 없이 MM-dd 형식으로 주고받는다`() {
        val authentication = UsernamePasswordAuthenticationToken(USER_ID, null, emptyList())
        val request = UpdateMyProfileRequest(nickname = "지유", birthday = MonthDay.of(10, 13))
        given(userService.updateMyProfile(USER_ID, request)).willReturn(
            MyProfileResponse(
                nickname = "지유",
                profileImageUrl = null,
                birthday = MonthDay.of(10, 13),
                wishProfileUrl = "https://botem.site/@key",
                kakaoConnected = true,
            ),
        )

        mockMvc.perform(
            put("/api/users/me/profile")
                .with(authentication(authentication))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{ "nickname": "지유", "birthday": "10-13" }"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.birthday").value("10-13"))
    }

    @Test
    fun `온보딩과 프로필 수정에서 생일을 비우면 400을 반환한다`() {
        val authentication = UsernamePasswordAuthenticationToken(USER_ID, null, emptyList())
        val requests = listOf(
            post("/api/users/me/onboarding")
                .content("""{ "name": "이정민", "bank": "TOSS_BANK", "accountNumber": "100159014821" }"""),
            put("/api/users/me/profile")
                .content("""{ "nickname": "지유", "birthday": null }"""),
        )

        requests.forEach { request ->
            mockMvc.perform(
                request
                    .with(authentication(authentication))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("COMMON_001"))
                .andExpect(jsonPath("$.error.detail").value(containsString("birthday")))
        }

        verifyNoInteractions(userService, onboardingService)
    }

    companion object {
        private const val USER_ID = 1L
    }
}
