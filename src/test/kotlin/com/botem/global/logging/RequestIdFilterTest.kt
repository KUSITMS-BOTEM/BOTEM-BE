package com.botem.global.logging

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RequestIdFilterTest {

    private val filter = RequestIdFilter()

    @Test
    fun `요청 ID가 없으면 새로 만들어 로그와 응답 헤더에 넣고 요청이 끝나면 지운다`() {
        val response = MockHttpServletResponse()
        var requestIdInChain: String? = null

        filter.doFilter(MockHttpServletRequest(), response, FilterChain { _, _ -> requestIdInChain = MDC.get("requestId") })

        val requestId = response.getHeader(RequestIdFilter.HEADER_NAME)
        assertTrue(Regex("[0-9a-f]{32}").matches(requestId.orEmpty()))
        assertEquals(requestId, requestIdInChain)
        assertNull(MDC.get("requestId"))
    }

    @Test
    fun `형식이 맞는 요청 ID는 이어 쓰고, 줄바꿈 등 형식이 틀리면 새로 만든다`() {
        val valid = runWith("front-1234")
        assertEquals("front-1234", valid)

        listOf("abc\nFAKE LOG", "a".repeat(65), "").forEach { invalid ->
            assertNotEquals(invalid, runWith(invalid))
        }
    }

    @Test
    fun `요청 처리 중 예외가 나도 요청 ID를 지운다`() {
        assertFailsWith<IllegalStateException> {
            filter.doFilter(
                MockHttpServletRequest(),
                MockHttpServletResponse(),
                FilterChain { _, _ -> throw IllegalStateException("실패") },
            )
        }

        assertNull(MDC.get("requestId"))
    }

    private fun runWith(incoming: String): String? {
        val request = MockHttpServletRequest().apply { addHeader(RequestIdFilter.HEADER_NAME, incoming) }
        val response = MockHttpServletResponse()
        filter.doFilter(request, response, FilterChain { _, _ -> })

        return response.getHeader(RequestIdFilter.HEADER_NAME)
    }
}
