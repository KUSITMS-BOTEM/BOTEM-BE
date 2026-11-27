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
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

// 공유 링크로 누구나 부를 수 있는 보태기·첨부 업로드 URL·비회원 확인을 IP당 분 단위로 제한한다.
// 서버를 여러 대로 늘려도 같은 한도를 적용하도록 카운터는 Redis에 둔다 (#282).
// ponytail: 고정 구간(1분) 카운터라 구간 경계에서 최대 2배까지 몰릴 수 있다. 더 고르게 막아야 하면 슬라이딩 윈도로 바꾼다
class RateLimitFilter(
    private val properties: RateLimitProperties,
    private val jsonMapper: JsonMapper,
    private val clock: Clock,
    private val counter: RateLimitCounter,
) : OncePerRequestFilter() {

    // Redis 장애 중에는 요청마다 실패하므로 경고는 구간(1분)마다 한 번만 남긴다
    private val lastWarnedWindow = AtomicLong(-1)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = policyOf(request) == null

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val policy = requireNotNull(policyOf(request))
        val nowMillis = clock.millis()
        val window = nowMillis / WINDOW_MILLIS
        val count = countOrNull("$KEY_PREFIX:${policy.name}:${clientIp(request)}:$window", window)

        // 카운터를 못 쓰면 정상 사용자를 막지 않도록 통과시킨다. 그동안은 제한 없이 동작한다
        if (count != null && count > policy.limit(properties)) {
            val retryAfterSeconds = (WINDOW_MILLIS - nowMillis % WINDOW_MILLIS + 999) / 1000
            rejectTooManyRequests(response, retryAfterSeconds)
            return
        }

        filterChain.doFilter(request, response)
    }

    private fun countOrNull(key: String, window: Long): Long? =
        try {
            counter.increment(key, KEY_EXPIRATION)
        } catch (exception: RuntimeException) {
            if (lastWarnedWindow.getAndSet(window) != window) {
                log.warn("요청 횟수 제한 카운터를 쓸 수 없어 제한 없이 통과시킵니다: {}", exception.message)
            }
            null
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
        private val log = LoggerFactory.getLogger(RateLimitFilter::class.java)
        private const val KEY_PREFIX = "rate-limit"
        private const val WINDOW_MILLIS = 60_000L
        // 구간이 끝난 키는 다시 읽지 않는다. 시계 차이를 감안해 구간보다 조금 길게 둔다
        private val KEY_EXPIRATION: Duration = Duration.ofMinutes(2)
        private const val REAL_IP_HEADER = "X-Real-IP"
    }
}
