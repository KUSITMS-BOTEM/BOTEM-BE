package com.botem.global.notification

import com.botem.global.property.DiscordProperties
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.core.task.SyncTaskExecutor
import org.springframework.http.HttpMethod
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.test.web.client.ExpectedCount.once
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent
import org.springframework.web.client.RestClient
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiscordErrorNotifierTest {

    private val webhookUrl = "https://discord.test/webhook"

    @Test
    fun `쿼리스트링 없이 요청 경로만 전송하고 같은 에러는 1분 내 한 번만 전송한다`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val notifier = DiscordErrorNotifier(DiscordProperties(webhookUrl), builder, SyncTaskExecutor())
        val request = MockHttpServletRequest("GET", "/api/v1/auth/callback").apply { queryString = "code=secret" }
        val exception = IllegalStateException("boom")

        server.expect(once(), requestTo(webhookUrl))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string(containsString("GET /api/v1/auth/callback")))
            .andExpect(content().string(not(containsString("secret"))))
            .andRespond(withNoContent())

        notifier.notify(exception, request)
        notifier.notify(exception, request)

        server.verify()
    }

    @Test
    fun `웹훅 URL이 없으면 전송하지 않는다`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val notifier = DiscordErrorNotifier(DiscordProperties(""), builder, SyncTaskExecutor())

        notifier.notify(IllegalStateException("boom"), null)

        server.verify()
    }

    @Test
    fun `예외 메시지는 500자로 자른다`() {
        val payload = DiscordErrorNotifier.buildPayload(
            exception = IllegalStateException("a".repeat(600)),
            requestLine = "GET /",
            userId = 1L,
            appFrames = emptyList(),
        ).toString()

        assertTrue(payload.contains("a".repeat(500)))
        assertFalse(payload.contains("a".repeat(501)))
    }

    @Test
    fun `감싸진 예외의 가장 안쪽 원인을 근본 원인으로 표시한다`() {
        val rootCause = java.sql.SQLException("Duplicate entry")
        val exception = IllegalStateException("outer", RuntimeException("middle", rootCause))

        val payload = DiscordErrorNotifier.buildPayload(
            exception = exception,
            requestLine = "GET /",
            userId = null,
            appFrames = emptyList(),
        ).toString()

        assertTrue(payload.contains("java.sql.SQLException: Duplicate entry"))
    }
}
