package com.botem.domain.auth.service

import com.botem.domain.user.entity.User
import com.botem.domain.user.entity.UserStatus
import com.botem.domain.user.exception.UserErrorCode
import com.botem.domain.user.repository.UserRepository
import com.botem.global.exception.BusinessException
import com.botem.global.jwt.JwtProvider
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.test.util.ReflectionTestUtils.setField
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AdminTestLoginServiceTest {

    private val userRepository = mock(UserRepository::class.java)
    private val jwtProvider = mock(JwtProvider::class.java)
    private val service = AdminTestLoginService(userRepository, jwtProvider)

    @Test
    fun `활성 회원 이메일로 기존 Access Token을 발급한다`() {
        given(userRepository.findAllByEmailAndStatus(EMAIL, UserStatus.ACTIVE)).willReturn(listOf(activeUser()))
        given(jwtProvider.createAccessToken(USER_ID)).willReturn(ACCESS_TOKEN)

        val response = service.issueAccessToken(EMAIL)

        assertEquals(ACCESS_TOKEN, response.accessToken)
        verify(userRepository).findAllByEmailAndStatus(EMAIL, UserStatus.ACTIVE)
        verify(jwtProvider).createAccessToken(USER_ID)
    }

    @Test
    fun `없는 이메일 또는 탈퇴 회원은 테스트 로그인할 수 없다`() {
        given(userRepository.findAllByEmailAndStatus(EMAIL, UserStatus.ACTIVE)).willReturn(emptyList())

        val exception = assertFailsWith<BusinessException> { service.issueAccessToken(EMAIL) }

        assertEquals(UserErrorCode.USER_NOT_FOUND, exception.errorCode)
        verifyNoInteractions(jwtProvider)
    }

    @Test
    fun `활성 회원 이메일이 중복되면 임의 계정의 토큰을 발급하지 않는다`() {
        given(userRepository.findAllByEmailAndStatus(EMAIL, UserStatus.ACTIVE))
            .willReturn(listOf(activeUser(USER_ID), activeUser(USER_ID + 1)))

        val exception = assertFailsWith<BusinessException> { service.issueAccessToken(EMAIL) }

        assertEquals(UserErrorCode.USER_NOT_FOUND, exception.errorCode)
        verifyNoInteractions(jwtProvider)
    }

    private fun activeUser(id: Long = USER_ID): User =
        User(kakaoId = id, nickname = "보탬", profileImageUrl = null, email = EMAIL).also {
            setField(it, "id", id)
        }

    companion object {
        private const val USER_ID = 1L
        private const val EMAIL = "demo@botem.com"
        private const val ACCESS_TOKEN = "test-access-token"
    }
}
