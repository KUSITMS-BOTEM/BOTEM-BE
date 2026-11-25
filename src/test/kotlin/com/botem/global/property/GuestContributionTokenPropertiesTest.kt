package com.botem.global.property

import jakarta.validation.Validation
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GuestContributionTokenPropertiesTest {

    private val validator = Validation.buildDefaultValidatorFactory().validator

    @Test
    fun `비회원 접근 토큰 만료 시간은 1초 이상이어야 한다`() {
        listOf(Duration.ZERO, Duration.ofMillis(999), Duration.ofSeconds(-1)).forEach { expiration ->
            val violations = validator.validate(GuestContributionTokenProperties(expiration))

            assertEquals(1, violations.size, expiration.toString())
            assertEquals("accessTokenExpirationValid", violations.single().propertyPath.toString())
        }
    }

    @Test
    fun `비회원 접근 토큰 만료 시간으로 1초 이상을 허용한다`() {
        assertTrue(validator.validate(GuestContributionTokenProperties(Duration.ofSeconds(1))).isEmpty())
        assertTrue(validator.validate(GuestContributionTokenProperties(Duration.ofMinutes(10))).isEmpty())
    }

    @Test
    fun `비회원 접근 토큰 issuer와 audience는 빈 값 또는 공백일 수 없다`() {
        listOf("", " ", "\t").forEach { blankValue ->
            val issuerViolations = validator.validate(
                GuestContributionTokenProperties(Duration.ofMinutes(10), issuer = blankValue),
            )
            val audienceViolations = validator.validate(
                GuestContributionTokenProperties(Duration.ofMinutes(10), audience = blankValue),
            )

            assertEquals(setOf("issuer"), issuerViolations.map { it.propertyPath.toString() }.toSet())
            assertEquals(setOf("audience"), audienceViolations.map { it.propertyPath.toString() }.toSet())
        }
    }
}
