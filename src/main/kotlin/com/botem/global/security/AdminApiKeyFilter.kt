package com.botem.global.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest

// 운영자 API는 회원 JWT가 있어도 통과시키지 않고, 키가 맞을 때만 운영자로 인증
class AdminApiKeyFilter(
    apiKey: String,
) : OncePerRequestFilter() {

    private val apiKeyBytes = apiKey.toByteArray()

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !request.requestURI.removePrefix(request.contextPath).startsWith(ADMIN_URI_PREFIX)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        SecurityContextHolder.clearContext()

        val requestKey = request.getHeader(ADMIN_API_KEY_HEADER)
        // 비교 시간으로 키를 추측하지 못하도록 고정 시간 비교
        if (requestKey != null && MessageDigest.isEqual(requestKey.toByteArray(), apiKeyBytes)) {
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(ADMIN_PRINCIPAL, null, listOf(SimpleGrantedAuthority(ROLE_ADMIN)))
        }

        filterChain.doFilter(request, response)
    }

    companion object {
        const val ADMIN_API_KEY_HEADER = "X-Admin-Key"
        const val ADMIN_URI_PATTERN = "/api/admin/**"
        private const val ADMIN_URI_PREFIX = "/api/admin/"
        private const val ADMIN_PRINCIPAL = "admin"
        private const val ROLE_ADMIN = "ROLE_ADMIN"
    }
}
