package com.botem.global.ratelimit

import com.botem.global.property.RateLimitProperties
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertContains
import kotlin.test.assertEquals

class RateLimitFilterTest {

    private val clock = MutableClock(Instant.parse("2026-11-21T03:00:10Z"))
    private val filter = RateLimitFilter(
        RateLimitProperties(contributionPerMinute = 2, attachmentUploadUrlPerMinute = 3, guestAccessPerMinute = 1),
        JsonMapper.builder().build(),
        clock,
        InMemoryRateLimitCounter(),
    )

    @Test
    fun `IP당 분당 한도를 넘으면 429와 다시 시도할 시간을 반환한다`() {
        assertEquals(200, post(CONTRIBUTIONS).status)
        assertEquals(200, post(MESSAGE_ONLY).status)

        val rejected = post(CONTRIBUTIONS)

        assertEquals(429, rejected.status)
        assertEquals("50", rejected.getHeader("Retry-After"))
        assertContains(rejected.contentAsString, "COMMON_429")
    }

    @Test
    fun `IP마다 따로 세고, nginx가 넣은 X-Real-IP를 클라이언트 IP로 쓴다`() {
        repeat(2) { assertEquals(200, post(CONTRIBUTIONS, realIp = "1.1.1.1").status) }

        assertEquals(429, post(CONTRIBUTIONS, realIp = "1.1.1.1").status)
        assertEquals(200, post(CONTRIBUTIONS, realIp = "2.2.2.2").status)
    }

    @Test
    fun `API 종류마다 한도를 따로 적용하고 다음 1분이 되면 다시 허용한다`() {
        repeat(3) { assertEquals(200, post(UPLOAD_URL).status) }
        assertEquals(429, post(UPLOAD_URL).status)
        assertEquals(200, post(GUEST_ACCESS).status)
        assertEquals(429, post(GUEST_ACCESS).status)

        clock.instant = clock.instant.plusSeconds(60)

        assertEquals(200, post(UPLOAD_URL).status)
        assertEquals(200, post(GUEST_ACCESS).status)
    }

    @Test
    fun `공유 화면 조회와 개설자 API는 제한하지 않는다`() {
        repeat(5) {
            assertEquals(200, request("GET", "/api/fundings/share/$SHARE_KEY").status)
            assertEquals(200, request("POST", "/api/fundings/10/contributions/decisions").status)
        }
    }

    @Test
    fun `카운터 저장소(Redis)에 장애가 나면 정상 사용자를 막지 않고 통과시킨다`() {
        val failingFilter = RateLimitFilter(
            RateLimitProperties(contributionPerMinute = 1, attachmentUploadUrlPerMinute = 1, guestAccessPerMinute = 1),
            JsonMapper.builder().build(),
            clock,
        ) { _, _ -> throw IllegalStateException("Redis 연결 실패") }

        repeat(3) {
            val response = MockHttpServletResponse()
            failingFilter.doFilter(MockHttpServletRequest("POST", CONTRIBUTIONS), response, FilterChain { _, _ -> })
            assertEquals(200, response.status)
        }
    }

    private fun post(path: String, realIp: String? = null): MockHttpServletResponse = request("POST", path, realIp)

    private fun request(method: String, path: String, realIp: String? = null): MockHttpServletResponse {
        val request = MockHttpServletRequest(method, path).apply {
            remoteAddr = "127.0.0.1"
            realIp?.let { addHeader("X-Real-IP", it) }
        }
        val response = MockHttpServletResponse()
        filter.doFilter(request, response, FilterChain { _, _ -> })

        return response
    }

    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("Asia/Seoul")

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = instant
    }

    companion object {
        private const val SHARE_KEY = "0123456789abcdef0123456789abcdef"
        private const val CONTRIBUTIONS = "/api/fundings/share/$SHARE_KEY/contributions"
        private const val MESSAGE_ONLY = "/api/fundings/share/$SHARE_KEY/contributions/message-only"
        private const val UPLOAD_URL = "/api/fundings/share/$SHARE_KEY/rolling-paper-attachments/upload-url"
        private const val GUEST_ACCESS = "/api/fundings/share/$SHARE_KEY/contributions/guest/access"
    }
}
