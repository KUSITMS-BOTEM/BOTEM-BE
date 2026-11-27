package com.botem.domain.friend.controller

import com.botem.domain.friend.dto.response.FriendProfileResponse
import com.botem.domain.friend.service.FriendProfileService
import com.botem.domain.user.repository.UserRepository
import com.botem.domain.user.service.UserStatusCache
import com.botem.global.config.SecurityConfig
import com.botem.global.exception.AuthErrorCode
import com.botem.global.exception.BusinessException
import com.botem.global.jwt.JwtAuthenticationEntryPoint
import com.botem.global.jwt.JwtProvider
import com.botem.global.property.AdminProperties
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [WishProfileController::class])
@Import(SecurityConfig::class)
@EnableConfigurationProperties(AdminProperties::class)
class WishProfileControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var friendProfileService: FriendProfileService

    @MockitoBean
    private lateinit var jwtProvider: JwtProvider

    @MockitoBean
    private lateinit var jwtAuthenticationEntryPoint: JwtAuthenticationEntryPoint

    @MockitoBean
    private lateinit var userRepository: UserRepository

    @MockitoBean
    private lateinit var userStatusCache: UserStatusCache

    @Test
    fun `로그인 없이 위시 프로필을 조회하면 위시는 잠긴 채로 반환한다`() {
        given(friendProfileService.getWishProfile(null, WISH_PROFILE_KEY)).willReturn(
            FriendProfileResponse(
                userId = 2L,
                name = "지유",
                profileImageUrl = null,
                birthday = null,
                birthdayDday = null,
                fundings = emptyList(),
                wishesVisible = false,
                wishCount = 2,
                wishes = emptyList(),
            ),
        )

        mockMvc.perform(get("/api/wish-profiles/$WISH_PROFILE_KEY"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.wishesVisible").value(false))
            .andExpect(jsonPath("$.data.wishCount").value(2))
    }

    @Test
    fun `만료된 토큰을 보내면 비로그인으로 낮추지 않고 401을 반환한다`() {
        given(jwtProvider.getUserIdFromAccessToken(EXPIRED_TOKEN))
            .willThrow(BusinessException(AuthErrorCode.EXPIRED_TOKEN))

        mockMvc.perform(get("/api/wish-profiles/$WISH_PROFILE_KEY").header("Authorization", "Bearer $EXPIRED_TOKEN"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AUTH_003"))

        verifyNoInteractions(friendProfileService)
    }

    companion object {
        private const val WISH_PROFILE_KEY = "8f0c6d779fae47f3a979302f2782d20f"
        private const val EXPIRED_TOKEN = "expired-token"
    }
}
