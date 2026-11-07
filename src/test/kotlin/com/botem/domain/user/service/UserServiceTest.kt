package com.botem.domain.user.service

import com.botem.domain.user.dto.request.UpdateMyProfileRequest
import com.botem.domain.user.entity.User
import com.botem.domain.user.entity.UserStatus
import com.botem.domain.user.exception.UserErrorCode
import com.botem.domain.user.repository.UserRepository
import com.botem.global.exception.AuthErrorCode
import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import com.botem.global.property.WishProfileProperties
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import java.time.MonthDay
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UserServiceTest {

    private val userRepository = mock(UserRepository::class.java)
    private val userService = UserService(
        userRepository,
        WishProfileProperties(baseUrl = "https://botem.site/"),
    )

    @Test
    fun `내 프로필을 조회한다`() {
        val birthday = MonthDay.of(10, 13)
        val user = User(
            kakaoId = 1234L,
            nickname = "지유",
            profileImageUrl = "https://example.com/profile.jpg",
            birthday = birthday,
            wishProfileKey = "8f0c6d779fae47f3a979302f2782d20f",
        )
        `when`(userRepository.findByIdForUpdate(1L)).thenReturn(user)

        val response = userService.getMyProfile(1L)

        assertEquals("지유", response.nickname)
        assertEquals("https://example.com/profile.jpg", response.profileImageUrl)
        assertEquals(birthday, response.birthday)
        assertEquals(
            "https://botem.site/@8f0c6d779fae47f3a979302f2782d20f",
            response.wishProfileUrl,
        )
        assertEquals(true, response.kakaoConnected)
    }

    @Test
    fun `카카오 연결이 해제된 사용자의 프로필에는 연결 상태를 false로 반환한다`() {
        val user = User(
            kakaoId = 1234L,
            nickname = "지유",
            profileImageUrl = null,
            kakaoConnected = false,
        )
        `when`(userRepository.findByIdForUpdate(1L)).thenReturn(user)

        val response = userService.getMyProfile(1L)

        assertEquals(false, response.kakaoConnected)
    }

    @Test
    fun `내 프로필을 수정하고 수정된 정보를 반환한다`() {
        val user = User(
            kakaoId = 1234L,
            nickname = "기존 닉네임",
            profileImageUrl = "https://example.com/old.jpg",
            wishProfileKey = "8f0c6d779fae47f3a979302f2782d20f",
        )
        val birthday = MonthDay.of(5, 20)
        val request = UpdateMyProfileRequest(
            nickname = " 새 닉네임 ",
            birthday = birthday,
        )
        `when`(userRepository.findByIdForUpdate(1L)).thenReturn(user)

        val response = userService.updateMyProfile(1L, request)

        assertEquals("새 닉네임", user.nickname)
        assertEquals("https://example.com/old.jpg", user.profileImageUrl)
        assertEquals(birthday, user.birthday)
        assertEquals("새 닉네임", response.nickname)
        assertEquals("https://example.com/old.jpg", response.profileImageUrl)
        assertEquals(birthday, response.birthday)
        assertEquals(
            "https://botem.site/@8f0c6d779fae47f3a979302f2782d20f",
            response.wishProfileUrl,
        )
    }

    @Test
    fun `온보딩 이름은 앞뒤 공백을 제거하고 생일과 함께 저장한다`() {
        val user = User(kakaoId = 1234L, nickname = "지유", profileImageUrl = null)
        `when`(userRepository.findByIdForUpdate(1L)).thenReturn(user)

        userService.updateOnboardingProfile(1L, " 이정민 ", MonthDay.of(10, 13))

        assertEquals("이정민", user.name)
        assertEquals(MonthDay.of(10, 13), user.birthday)
    }

    @Test
    fun `온보딩 이름이 공백뿐이면 예외가 발생한다`() {
        val user = User(kakaoId = 1234L, nickname = "지유", profileImageUrl = null)
        `when`(userRepository.findByIdForUpdate(1L)).thenReturn(user)

        val exception = assertFailsWith<BusinessException> {
            userService.updateOnboardingProfile(1L, "\u3000 ", null)
        }

        assertEquals(CommonErrorCode.INVALID_INPUT_VALUE, exception.errorCode)
    }

    @Test
    fun `존재하지 않는 사용자의 프로필을 조회하면 예외가 발생한다`() {
        val exception = assertFailsWith<BusinessException> {
            userService.getMyProfile(1L)
        }

        assertEquals(UserErrorCode.USER_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `공백 문자로만 이루어진 닉네임은 수정할 수 없다`() {
        val user = User(
            kakaoId = 1234L,
            nickname = "기존 닉네임",
            profileImageUrl = null,
        )
        val request = UpdateMyProfileRequest(
            nickname = "\u00A0\u00A0",
            birthday = null,
        )
        `when`(userRepository.findByIdForUpdate(1L)).thenReturn(user)

        val exception = assertFailsWith<BusinessException> {
            userService.updateMyProfile(1L, request)
        }

        assertEquals(CommonErrorCode.INVALID_INPUT_VALUE, exception.errorCode)
        assertEquals("nickname: 공백일 수 없습니다", exception.detail)
        assertEquals("기존 닉네임", user.nickname)
    }

    @Test
    fun `닉네임 길이는 앞뒤 공백을 제거한 값으로 검증한다`() {
        val user = User(
            kakaoId = 1234L,
            nickname = "기존 닉네임",
            profileImageUrl = null,
        )
        val nickname = "가".repeat(255)
        val request = UpdateMyProfileRequest(
            nickname = " $nickname ",
            birthday = null,
        )
        `when`(userRepository.findByIdForUpdate(1L)).thenReturn(user)

        val response = userService.updateMyProfile(1L, request)

        assertEquals(nickname, response.nickname)
        assertEquals(nickname, user.nickname)
    }

    @Test
    fun `공백을 제거한 닉네임이 255자를 초과하면 수정할 수 없다`() {
        val user = User(
            kakaoId = 1234L,
            nickname = "기존 닉네임",
            profileImageUrl = null,
        )
        val request = UpdateMyProfileRequest(
            nickname = "가".repeat(256),
            birthday = null,
        )
        `when`(userRepository.findByIdForUpdate(1L)).thenReturn(user)

        val exception = assertFailsWith<BusinessException> {
            userService.updateMyProfile(1L, request)
        }

        assertEquals(CommonErrorCode.INVALID_INPUT_VALUE, exception.errorCode)
        assertEquals("nickname: 255자 이하여야 합니다", exception.detail)
        assertEquals("기존 닉네임", user.nickname)
    }

    @Test
    fun `위시 프로필 키가 없으면 조건부 갱신으로 생성한다`() {
        val user = User(
            kakaoId = 1234L,
            nickname = "지유",
            profileImageUrl = null,
            wishProfileKey = null,
        )
        `when`(userRepository.findByIdForUpdate(1L)).thenReturn(user)
        `when`(userRepository.updateWishProfileKeyIfNull(eq(0L), anyString())).thenReturn(1)

        val response = userService.getMyProfile(1L)
        val key = response.wishProfileUrl.substringAfter("/@")

        assertEquals(32, key.length)
        assertEquals(true, key.all { it.isDigit() || it in 'a'..'f' })
    }

    @Test
    fun `동시에 생성된 위시 프로필 키가 있으면 먼저 저장된 키를 반환한다`() {
        val winnerKey = "8f0c6d779fae47f3a979302f2782d20f"
        val user = User(
            kakaoId = 1234L,
            nickname = "지유",
            profileImageUrl = null,
            wishProfileKey = null,
        )
        `when`(userRepository.findByIdForUpdate(1L)).thenReturn(user)
        `when`(userRepository.updateWishProfileKeyIfNull(eq(0L), anyString())).thenReturn(0)
        `when`(userRepository.findWishProfileKeyForUpdate(0L)).thenReturn(winnerKey)

        val response = userService.getMyProfile(1L)

        assertEquals("https://botem.site/@$winnerKey", response.wishProfileUrl)
    }

    @Test
    fun `탈퇴한 사용자의 프로필을 조회하면 위시 프로필 키를 생성하지 않는다`() {
        val user = User(
            kakaoId = 1234L,
            nickname = "탈퇴한 사용자",
            profileImageUrl = null,
            wishProfileKey = null,
            kakaoConnected = false,
            status = UserStatus.WITHDRAWN,
        )
        `when`(userRepository.findByIdForUpdate(1L)).thenReturn(user)

        val exception = assertFailsWith<BusinessException> {
            userService.getMyProfile(1L)
        }

        assertEquals(AuthErrorCode.WITHDRAWN_USER, exception.errorCode)
        verify(userRepository).findByIdForUpdate(1L)
        verifyNoMoreInteractions(userRepository)
    }
}
