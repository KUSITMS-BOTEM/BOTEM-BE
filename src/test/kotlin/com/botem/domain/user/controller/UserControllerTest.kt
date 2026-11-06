package com.botem.domain.user.controller

import com.botem.domain.user.service.UserService
import com.botem.domain.user.service.UserWithdrawalService
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [UserController::class])
class UserControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var userService: UserService

    @MockitoBean
    private lateinit var userWithdrawalService: UserWithdrawalService

    @Test
    fun `회원 탈퇴에 성공하면 Refresh Token 쿠키를 만료시킨다`() {
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
                header().string(
                    HttpHeaders.SET_COOKIE,
                    allOf(
                        containsString("refreshToken="),
                        containsString("Path=/api/auth"),
                        containsString("Max-Age=0"),
                        containsString("Secure"),
                        containsString("HttpOnly"),
                        containsString("SameSite=Lax"),
                    ),
                ),
            )

        verify(userWithdrawalService).withdraw(USER_ID)
    }

    companion object {
        private const val USER_ID = 1L
    }
}
