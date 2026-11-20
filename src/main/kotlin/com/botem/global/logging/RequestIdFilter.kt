package com.botem.global.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

// 요청마다 ID를 붙여 같은 요청의 로그를 묶어 보고, 응답 헤더로 돌려줘 프론트가 오류 문의 때 전달할 수 있게 한다.
// 인증 실패 로그에도 남도록 Spring Security보다 먼저 실행한다
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        // 받은 값을 그대로 로그에 쓰면 줄바꿈 등으로 로그를 위조할 수 있어 형식이 맞을 때만 이어 쓴다
        val requestId = request.getHeader(HEADER_NAME)
            ?.takeIf(REQUEST_ID_REGEX::matches)
            ?: UUID.randomUUID().toString().replace("-", "")

        MDC.put(MDC_KEY, requestId)
        response.setHeader(HEADER_NAME, requestId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(MDC_KEY)
        }
    }

    companion object {
        const val HEADER_NAME = "X-Request-Id"
        const val MDC_KEY = "requestId"
        private val REQUEST_ID_REGEX = Regex("[A-Za-z0-9-]{1,64}")
    }
}
