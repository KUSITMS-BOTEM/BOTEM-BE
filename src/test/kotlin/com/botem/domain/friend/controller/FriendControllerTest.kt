package com.botem.domain.friend.controller

import com.botem.domain.friend.dto.response.FriendListResponse
import com.botem.domain.friend.dto.response.FriendResponse
import com.botem.domain.friend.exception.FriendErrorCode
import com.botem.domain.friend.service.FriendService
import com.botem.global.exception.BusinessException
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willThrow
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.MonthDay

@WebMvcTest(controllers = [FriendController::class])
class FriendControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var friendService: FriendService

    @Test
    fun `위시 프로필 키로 친구를 맺는다`() {
        mockMvc.perform(
            post("/api/friends")
                .with(loggedIn())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{ "wishProfileKey": "$WISH_PROFILE_KEY" }"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        verify(friendService).addFriend(USER_ID, WISH_PROFILE_KEY)
    }

    @Test
    fun `위시 프로필 키가 비어 있으면 400을 반환한다`() {
        mockMvc.perform(
            post("/api/friends")
                .with(loggedIn())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{ "wishProfileKey": " " }"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("COMMON_001"))

        verifyNoInteractions(friendService)
    }

    @Test
    fun `친구 목록을 생일 MM-dd 형식으로 조회한다`() {
        val friend = FriendResponse(
            userId = FRIEND_ID,
            name = "민지",
            profileImageUrl = null,
            birthday = MonthDay.of(10, 24),
            birthdayDday = 3,
            wishCount = 2,
        )
        given(friendService.getFriends(USER_ID)).willReturn(
            FriendListResponse(totalCount = 1, upcomingBirthdayFriends = listOf(friend), friends = listOf(friend)),
        )

        mockMvc.perform(get("/api/friends").with(loggedIn()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.totalCount").value(1))
            .andExpect(jsonPath("$.data.upcomingBirthdayFriends[0].birthdayDday").value(3))
            .andExpect(jsonPath("$.data.friends[0].name").value("민지"))
            .andExpect(jsonPath("$.data.friends[0].birthday").value("10-24"))
            .andExpect(jsonPath("$.data.friends[0].wishCount").value(2))
    }

    @Test
    fun `친구를 끊고 친구가 아니면 404를 반환한다`() {
        mockMvc.perform(delete("/api/friends/$FRIEND_ID").with(loggedIn()).with(csrf()))
            .andExpect(status().isOk)

        verify(friendService).deleteFriend(USER_ID, FRIEND_ID)

        willThrow(BusinessException(FriendErrorCode.FRIEND_NOT_FOUND)).given(friendService).deleteFriend(USER_ID, FRIEND_ID)

        mockMvc.perform(delete("/api/friends/$FRIEND_ID").with(loggedIn()).with(csrf()))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("FRIEND_002"))
    }

    private fun loggedIn() = authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList()))

    companion object {
        private const val USER_ID = 1L
        private const val FRIEND_ID = 2L
        private const val WISH_PROFILE_KEY = "8f0c6d779fae47f3a979302f2782d20f"
    }
}
