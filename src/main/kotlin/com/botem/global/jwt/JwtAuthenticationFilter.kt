package com.botem.global.jwt

import com.botem.domain.user.entity.UserStatus
import com.botem.domain.user.repository.UserRepository
import com.botem.global.exception.AuthErrorCode
import com.botem.global.exception.BusinessException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class JwtAuthenticationFilter(
    private val jwtProvider: JwtProvider,
    private val userRepository: UserRepository,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = resolveToken(request)

        if (token != null) {
            try {
                val userId = jwtProvider.getUserIdFromAccessToken(token)
                validateActiveUser(userId, request)
                SecurityContextHolder.getContext().authentication =
                    UsernamePasswordAuthenticationToken(userId, null, emptyList())
            } catch (exception: BusinessException) {
                request.setAttribute(ERROR_CODE_ATTRIBUTE, exception.errorCode)
            }
        }

        filterChain.doFilter(request, response)
    }

    private fun validateActiveUser(userId: Long, request: HttpServletRequest) {
        when (userRepository.findStatusById(userId)) {
            UserStatus.ACTIVE -> Unit
            UserStatus.WITHDRAWN -> {
                if (!isWithdrawalRequest(request)) {
                    throw BusinessException(AuthErrorCode.WITHDRAWN_USER)
                }
            }
            null -> throw BusinessException(AuthErrorCode.INVALID_TOKEN)
        }
    }

    private fun isWithdrawalRequest(request: HttpServletRequest): Boolean =
        request.method == WITHDRAWAL_METHOD &&
            request.requestURI.removePrefix(request.contextPath) == WITHDRAWAL_URI

    private fun resolveToken(request: HttpServletRequest): String? {
        val authorization = request.getHeader(HttpHeaders.AUTHORIZATION) ?: return null

        return authorization.takeIf { it.startsWith(BEARER_PREFIX) }?.substring(BEARER_PREFIX.length)
    }

    companion object {
        const val ERROR_CODE_ATTRIBUTE = "jwtErrorCode"
        private const val BEARER_PREFIX = "Bearer "
        private const val WITHDRAWAL_METHOD = "DELETE"
        private const val WITHDRAWAL_URI = "/api/users/me"
    }
}
