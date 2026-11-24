package com.botem.global.config

import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertTrue

class JpaAuditingConfigTest {

    @Test
    fun `감사 시각은 UTC 기준으로 생성한다`() {
        val before = LocalDateTime.now(ZoneOffset.UTC)

        val auditedAt = JpaAuditingConfig().utcDateTimeProvider().now.orElseThrow() as LocalDateTime

        val after = LocalDateTime.now(ZoneOffset.UTC)
        assertTrue(auditedAt in before..after)
    }
}
