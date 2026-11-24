package com.botem.global.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Optional

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "utcDateTimeProvider")
class JpaAuditingConfig {

    @Bean
    fun utcDateTimeProvider(): DateTimeProvider =
        DateTimeProvider { Optional.of(LocalDateTime.now(ZoneOffset.UTC)) }
}
