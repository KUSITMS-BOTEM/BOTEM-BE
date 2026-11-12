package com.botem.domain.user.service

import com.botem.domain.user.dto.request.UpdateMyProfileRequest
import com.botem.domain.user.entity.User
import com.botem.domain.user.entity.UserStatus
import com.botem.domain.user.exception.UserErrorCode
import com.botem.domain.user.repository.UserRepository
import com.botem.global.entity.BaseEntity
import com.botem.global.exception.AuthErrorCode
import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import com.botem.global.property.WishProfileProperties
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions

class UserServiceTest {

    private val userRepository = mock(UserRepository::class.java)
    private val profileImageService = mock(ProfileImageService::class.java)
    private val userService = UserService(
        userRepository,
        WishProfileProperties(baseUrl = "https://botem.site/"),
        profileImageService,
    )

    @Test
    fun `내 프로필을 조회한다`() {
        val birthday = MonthDay.of(10, 13)
        val user = User(
            kakaoId = 1234L,
            nickname = "지유",
            profileImageUrl = "https://example.com/profile.jpg",
            birthday = birthday,
            email = "jiyu@kakao.com",
            wishProfileKey = "8f0c6d779fae47f3a979302f2782d20f",
        )
        `when`(userRepository.findByIdForUpdate(1L)).thenReturn(user.joined())

        val response = userService.getMyProfile(1L)

        assertEquals("지유", response.nickname)
        assertEquals("https://example.com/profile.jpg", response.profileImageUrl)
        assertEquals(birthday, response.birthday)
        assertEquals(
            "https://botem.site/@8f0c6d779fae47f3a979302f2782d20f",
            response.wishProfileUrl,
        )
        assertEquals(true, response.kakaoConnected)
        assertEquals("jiyu@kakao.com", response.email)
        // 한국 시간 00:30(UTC 전날 15:30)에 가입해도 가입일은 한국 날짜
        assertEquals(LocalDate.of(2026, 11, 10), response.joinedAt)
    }

    @Test
    fun `카카오 연결이 해제된 사용자의 프로필에는 연결 상태를 false로 반환한다`() {
        val user = User(
            kakaoId = 1234L,
            nickname = "지유",
            profileImageUrl = null,
            kakaoConnected = false,
        )
        `when`(userRepository.findByIdForUpdate(1L)).thenReturn(user.joined())

        val response = userService.getMyProfile(1L)

        assertEquals(false, response.kakaoConnected)
    }

    @Test
    fun `내 프로필의 이름과 생일을 수정하고 카카오 닉네임과 기존 이미지는 유지한다`() {
        val user = User(
            kakaoId = 1234L,
            nickname = "지유",
            profileImageUrl = "https://example.com/old.jpg",
            name = "기존 이름",
            wishProfileKey = "8f0c6d779fae47f3a979302f2782d20f",
        )
        val birthday = MonthDay.of(5, 20)
        `when`(userRepository.findByIdForUpdate(1L)).thenReturn(user.joined())

        val response = userService.updateMyProfile(1L, UpdateMyProfileRequest(name = " 이정민 ", birthday = birthday))

        assertEquals("이정민", user.name)
        assertEquals(birthday, user.birthday)
        assertEquals("지유", user.nickname)
        assertEquals("https://example.com/old.jpg", user.profileImageUrl)
        assertEquals("이정민", response.name)
        assertEquals("지유", response.nickname)
        assertEquals(birthday, response.birthday)
        assertEquals(
            "https://botem.site/@8f0c6d779fae47f3a979302f2782d20f",
            response.wishProfileUrl,
        )
    }

    @Test
    fun `프로필 이미지 URL을 전달하면 검증 후 변경한다`() {
        val user = User(kakaoId = 1234L, nickname = "지유", profileImageUrl = "https://example.com/old.jpg")
        val imageUrl = "https://botem-images.s3.ap-northeast-2.amazonaws.com/profile/0/new.jpg"
        `when`(userRepository.findByIdForUpdate(1L)).thenReturn(user.joined())

        val response = userService.updateMyProfile(
            1L,
            UpdateMyProfileRequest(name = "이정민", birthday = MonthDay.of(10, 13), profileImageUrl = imageUrl),
        )

        verify(profileImageService).validateImageUrl(user.id, imageUrl)
        assertEquals(imageUrl, user.profileImageUrl)
        assertEquals(imageUrl, response.profileImageUrl)
    }

    @Test
    fun `허용되지 않은 프로필 이미지 URL이면 아무것도 변경하지 않는다`() {
        val user = User(kakaoId = 1234L, nickname = "지유", profileImageUrl = "https://example.com/old.jpg", name = "기존 이름")
        val imageUrl = "https://evil.example.com/a.jpg"
        `when`(userRepository.findByIdForUpdate(1L)).thenReturn(user.joined())
        `when`(profileImageService.validateImageUrl(user.id, imageUrl))
            .thenThrow(BusinessException(CommonErrorCode.INVALID_INPUT_VALUE))

        assertFailsWith<BusinessException> {
            userService.updateMyProfile(
                1L,
                UpdateMyProfileRequest(name = "이정민", birthday = MonthDay.of(10, 13), profileImageUrl = imageUrl),
            )
        }

        assertEquals("기존 이름", user.name)
        assertEquals("https://example.com/old.jpg", user.profileImageUrl)
    }

    @Test
    fun `온보딩 이름은 앞뒤 공백을 제거하고 생일과 함께 저장한다`() {
        val user = User(kakaoId = 1234L, nickname = "지유", profileImageUrl = null)
        `when`(userRepository.findByIdForUpdate(1L)).thenReturn(user.joined())

        userService.updateOnboardingProfile(1L, " 이정민 ", MonthDay.of(10, 13), null)

        assertEquals("이정민", user.name)
        assertEquals(MonthDay.of(10, 13), user.birthday)
    }

    @Test
    fun `온보딩 이름이 공백뿐이면 예외가 발생한다`() {
        val user = User(kakaoId = 1234L, nickname = "지유", profileImageUrl = null)
        `when`(userRepository.findByIdForUpdate(1L)).thenReturn(user.joined())

        val exception = assertFailsWith<BusinessException> {
            userService.updateOnboardingProfile(1L, "\u3000 ", null, null)
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
    fun `공백 문자로만 이루어진 이름으로는 수정할 수 없다`() {
        val user = User(kakaoId = 1234L, nickname = "지유", profileImageUrl = null, name = "기존 이름")
        `when`(userRepository.findByIdForUpdate(1L)).thenReturn(user.joined())

        val exception = assertFailsWith<BusinessException> {
            userService.updateMyProfile(1L, UpdateMyProfileRequest(name = "\u00A0\u00A0", birthday = MonthDay.of(10, 13)))
        }

        assertEquals(CommonErrorCode.INVALID_INPUT_VALUE, exception.errorCode)
        assertEquals("name: 공백일 수 없습니다", exception.detail)
        assertEquals("기존 이름", user.name)
    }

    @Test
    fun `공백을 제거한 이름은 50자까지 수정할 수 있고 넘으면 수정할 수 없다`() {
        val user = User(kakaoId = 1234L, nickname = "지유", profileImageUrl = null)
        `when`(userRepository.findByIdForUpdate(1L)).thenReturn(user.joined())
        val maxName = "가".repeat(50)

        userService.updateMyProfile(1L, UpdateMyProfileRequest(name = " $maxName ", birthday = MonthDay.of(10, 13)))

        assertEquals(maxName, user.name)

        val exception = assertFailsWith<BusinessException> {
            userService.updateMyProfile(1L, UpdateMyProfileRequest(name = "가".repeat(51), birthday = MonthDay.of(10, 13)))
        }

        assertEquals("name: 50자 이하여야 합니다", exception.detail)
        assertEquals(maxName, user.name)
    }

    @Test
    fun `위시 프로필 키가 없으면 조건부 갱신으로 생성한다`() {
        val user = User(
            kakaoId = 1234L,
            nickname = "지유",
            profileImageUrl = null,
            wishProfileKey = null,
        )
        `when`(userRepository.findByIdForUpdate(1L)).thenReturn(user.joined())
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
        `when`(userRepository.findByIdForUpdate(1L)).thenReturn(user.joined())
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
        `when`(userRepository.findByIdForUpdate(1L)).thenReturn(user.joined())

        val exception = assertFailsWith<BusinessException> {
            userService.getMyProfile(1L)
        }

        assertEquals(AuthErrorCode.WITHDRAWN_USER, exception.errorCode)
        verify(userRepository).findByIdForUpdate(1L)
        verifyNoMoreInteractions(userRepository)
    }

    // 저장 시 채워지는 가입 시각을 UTC 2026-11-09 15:30(한국 2026-11-10 00:30)으로 지정
    private fun User.joined(): User = apply {
        val createdAt = ZonedDateTime.of(2026, 11, 9, 15, 30, 0, 0, ZoneOffset.UTC)
            .withZoneSameInstant(ZoneId.systemDefault())
            .toLocalDateTime()
        BaseEntity::class.java.getDeclaredField("createdAt").apply {
            isAccessible = true
            set(this@joined, createdAt)
        }
    }
}
