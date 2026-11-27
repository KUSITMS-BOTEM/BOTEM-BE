package com.botem.global.jwt

import com.botem.domain.user.entity.UserStatus
import com.botem.domain.user.repository.UserRepository
import com.botem.global.exception.AuthErrorCode
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JwtAuthenticationFilterTest {

    private val jwtProvider = mock(JwtProvider::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val filterChain = mock(FilterChain::class.java)
    private val filter = JwtAuthenticationFilter(jwtProvider, userRepository::findStatusById)

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `탈퇴 사용자는 회원 탈퇴 재요청에만 인증된다`() {
        val request = request(method = "DELETE", uri = "/api/users/me")
        val response = MockHttpServletResponse()
        `when`(jwtProvider.getUserIdFromAccessToken(ACCESS_TOKEN)).thenReturn(USER_ID)
        `when`(userRepository.findStatusById(USER_ID)).thenReturn(UserStatus.WITHDRAWN)

        filter.doFilter(request, response, filterChain)

        val authentication = assertNotNull(SecurityContextHolder.getContext().authentication)
        assertEquals(USER_ID, authentication.principal)
        assertNull(request.getAttribute(JwtAuthenticationFilter.ERROR_CODE_ATTRIBUTE))
        verify(filterChain).doFilter(request, response)
    }

    @Test
    fun `탈퇴 사용자의 다른 API 접근은 계속 차단한다`() {
        val request = request(method = "GET", uri = "/api/users/me/profile")
        val response = MockHttpServletResponse()
        `when`(jwtProvider.getUserIdFromAccessToken(ACCESS_TOKEN)).thenReturn(USER_ID)
        `when`(userRepository.findStatusById(USER_ID)).thenReturn(UserStatus.WITHDRAWN)

        filter.doFilter(request, response, filterChain)

        assertNull(SecurityContextHolder.getContext().authentication)
        assertEquals(
            AuthErrorCode.WITHDRAWN_USER,
            request.getAttribute(JwtAuthenticationFilter.ERROR_CODE_ATTRIBUTE),
        )
        verify(filterChain).doFilter(request, response)
    }

    @Test
    fun `서명이 유효해도 DB에 없는 회원의 토큰이면 인증하지 않고 INVALID_TOKEN을 남긴다`() {
        val request = request(method = "GET", uri = "/api/users/me/profile")
        val response = MockHttpServletResponse()
        `when`(jwtProvider.getUserIdFromAccessToken(ACCESS_TOKEN)).thenReturn(USER_ID)
        `when`(userRepository.findStatusById(USER_ID)).thenReturn(null)

        filter.doFilter(request, response, filterChain)

        assertNull(SecurityContextHolder.getContext().authentication)
        assertEquals(AuthErrorCode.INVALID_TOKEN, request.getAttribute(JwtAuthenticationFilter.ERROR_CODE_ATTRIBUTE))
        verify(filterChain).doFilter(request, response)
    }

    @Test
    fun `Bearer 형식이 아니거나 헤더가 없으면 토큰을 검사하지 않고 비로그인으로 넘긴다`() {
        val requests = listOf(
            MockHttpServletRequest("GET", "/api/home/fundings").apply {
                addHeader(HttpHeaders.AUTHORIZATION, "Basic $ACCESS_TOKEN")
            },
            MockHttpServletRequest("GET", "/api/home/fundings"),
        )

        requests.forEach { request ->
            val response = MockHttpServletResponse()

            filter.doFilter(request, response, filterChain)

            assertNull(SecurityContextHolder.getContext().authentication)
            assertNull(request.getAttribute(JwtAuthenticationFilter.ERROR_CODE_ATTRIBUTE))
            verify(filterChain).doFilter(request, response)
        }
        verifyNoInteractions(jwtProvider, userRepository)
    }

    private fun request(method: String, uri: String): MockHttpServletRequest =
        MockHttpServletRequest(method, uri).apply {
            addHeader(HttpHeaders.AUTHORIZATION, "Bearer $ACCESS_TOKEN")
        }

    companion object {
        private const val USER_ID = 1L
        private const val ACCESS_TOKEN = "access-token"
    }
}
