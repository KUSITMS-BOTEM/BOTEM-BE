package com.botem.global.monitoring

import io.sentry.spring.boot4.SentryProperties
import io.sentry.util.TracingUtils
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// 실제 설정을 읽은 Sentry 옵션으로, 모니터링 수집·헬스 체크만 성능 추적에서 빠지는지 확인한다.
// Sentry는 DSN이 있어야 설정을 읽으므로 전송되지 않는 가짜 DSN을 쓴다
@SpringBootTest(properties = ["sentry.dsn=https://key@localhost/0"])
class SentryTracingFilterTest {

    @Autowired
    private lateinit var sentryProperties: SentryProperties

    @Test
    fun `actuator 요청은 성능 추적에서 빼고 API 요청은 추적한다`() {
        val ignored = sentryProperties.ignoredTransactions

        assertTrue(TracingUtils.isIgnored(ignored, "GET /actuator/prometheus"))
        assertTrue(TracingUtils.isIgnored(ignored, "GET /actuator/health"))
        assertFalse(TracingUtils.isIgnored(ignored, "GET /api/home/fundings"))
        assertFalse(TracingUtils.isIgnored(ignored, "POST /api/fundings/share/{shareKey}/contributions"))
    }
}
