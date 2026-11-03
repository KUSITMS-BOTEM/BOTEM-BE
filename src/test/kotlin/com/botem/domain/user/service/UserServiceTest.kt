package com.botem.domain.user.service

import com.botem.domain.user.dto.request.UpdateMyProfileRequest
import com.botem.domain.user.entity.User
import com.botem.domain.user.exception.UserErrorCode
import com.botem.domain.user.repository.UserRepository
import com.botem.global.exception.BusinessException
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.LocalDate
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UserServiceTest {

    private val userRepository = mock(UserRepository::class.java)
    private val userService = UserService(userRepository)

    @Test
    fun `내 프로필을 조회한다`() {
        val birthday = LocalDate.of(2000, 10, 13)
        val user = User(
            kakaoId = 1234L,
            nickname = "지유",
            profileImageUrl = "https://example.com/profile.jpg",
            birthday = birthday,
        )
        `when`(userRepository.findById(1L)).thenReturn(Optional.of(user))

        val response = userService.getMyProfile(1L)

        assertEquals("지유", response.nickname)
        assertEquals("https://example.com/profile.jpg", response.profileImageUrl)
        assertEquals(birthday, response.birthday)
        assertTrue(response.kakaoConnected)
    }

    @Test
    fun `내 프로필을 수정하고 수정된 정보를 반환한다`() {
        val user = User(
            kakaoId = 1234L,
            nickname = "기존 닉네임",
            profileImageUrl = "https://example.com/old.jpg",
        )
        val birthday = LocalDate.of(2001, 5, 20)
        val request = UpdateMyProfileRequest(
            nickname = " 새 닉네임 ",
            birthday = birthday,
        )
        `when`(userRepository.findById(1L)).thenReturn(Optional.of(user))

        val response = userService.updateMyProfile(1L, request)

        assertEquals("새 닉네임", user.nickname)
        assertEquals("https://example.com/old.jpg", user.profileImageUrl)
        assertEquals(birthday, user.birthday)
        assertEquals("새 닉네임", response.nickname)
        assertEquals("https://example.com/old.jpg", response.profileImageUrl)
        assertEquals(birthday, response.birthday)
    }

    @Test
    fun `존재하지 않는 사용자의 프로필을 조회하면 예외가 발생한다`() {
        `when`(userRepository.findById(1L)).thenReturn(Optional.empty())

        val exception = assertFailsWith<BusinessException> {
            userService.getMyProfile(1L)
        }

        assertEquals(UserErrorCode.USER_NOT_FOUND, exception.errorCode)
    }
}
