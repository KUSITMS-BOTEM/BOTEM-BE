package com.botem.global.ratelimit

import com.botem.global.exception.CommonErrorCode
import com.botem.global.property.RateLimitProperties
import com.botem.global.response.ApiResponse
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

// 공유 링크로 누구나 부를 수 있는 보태기·첨부 업로드 URL·비회원 확인을 IP당 분 단위로 제한한다.
// ponytail: 서버 한 대라 메모리의 고정 구간(1분) 카운터로 충분하다. 구간 경계에서 최대 2배까지 몰릴 수 있고,
// 서버를 여러 대로 늘리면 Redis 같은 공용 저장소로 옮겨야 한다
class RateLimitFilter(
    private val properties: RateLimitProperties,
    private val jsonMapper: JsonMapper,
    private val clock: Clock,
) : OncePerRequestFilter() {

    private val counts = ConcurrentHashMap<String, AtomicInteger>()

    @Volatile
    private var currentWindow = -1L

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = policyOf(request) == null

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val policy = requireNotNull(policyOf(request))
        val nowMillis = clock.millis()
        val window = nowMillis / WINDOW_MILLIS
        resetIfNewWindow(window)

        val count = counts.computeIfAbsent("${policy.name}:${clientIp(request)}") { AtomicInteger() }.incrementAndGet()
        if (count > policy.limit(properties)) {
            val retryAfterSeconds = (WINDOW_MILLIS - nowMillis % WINDOW_MILLIS + 999) / 1000
            rejectTooManyRequests(response, retryAfterSeconds)
            return
        }

        filterChain.doFilter(request, response)
    }

    // 지난 구간의 카운터는 더 쓰지 않으므로 구간이 바뀔 때 한 번에 비운다
    private fun resetIfNewWindow(window: Long) {
        if (window == currentWindow) {
            return
        }
        synchronized(this) {
            if (window != currentWindow) {
                counts.clear()
                currentWindow = window
            }
        }
    }

    // nginx가 실제 접속 IP로 덮어쓰는 X-Real-IP를 쓴다. X-Forwarded-For 맨 앞 값은 클라이언트가 위조할 수 있다
    private fun clientIp(request: HttpServletRequest): String =
        request.getHeader(REAL_IP_HEADER)?.takeIf(String::isNotBlank) ?: request.remoteAddr

    private fun rejectTooManyRequests(response: HttpServletResponse, retryAfterSeconds: Long) {
        response.status = CommonErrorCode.TOO_MANY_REQUESTS.status.value()
        response.setHeader(HttpHeaders.RETRY_AFTER, retryAfterSeconds.toString())
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        response.writer.write(jsonMapper.writeValueAsString(ApiResponse.fail(CommonErrorCode.TOO_MANY_REQUESTS)))
    }

    private fun policyOf(request: HttpServletRequest): Policy? {
        if (request.method != HttpMethod.POST.name()) {
            return null
        }
        val path = request.requestURI.removePrefix(request.contextPath)

        return Policy.entries.firstOrNull { it.pathRegex.matches(path) }
    }

    private enum class Policy(val pathRegex: Regex, val limit: (RateLimitProperties) -> Int) {
        CONTRIBUTION(
            Regex("/api/fundings/share/[^/]+/contributions(/message-only)?"),
            RateLimitProperties::contributionPerMinute,
        ),
        ATTACHMENT_UPLOAD_URL(
            Regex("/api/fundings/share/[^/]+/rolling-paper-attachments/upload-url"),
            RateLimitProperties::attachmentUploadUrlPerMinute,
        ),
        GUEST_ACCESS(
            Regex("/api/fundings/share/[^/]+/contributions/guest/access"),
            RateLimitProperties::guestAccessPerMinute,
        ),
    }

    companion object {
        private const val WINDOW_MILLIS = 60_000L
        private const val REAL_IP_HEADER = "X-Real-IP"
    }
}
