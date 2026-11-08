package com.botem.global.property

import jakarta.validation.Validation
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthCookiePropertiesTest {

    private val validator = Validation.buildDefaultValidatorFactory().validator

    @Test
    fun `쿠키 도메인이 올바르지 않으면 검증에 실패한다`() {
        val invalidDomains = listOf(
            "\${AUTH_COOKIE_DOMAIN}",
            "",
            " ",
            "https://botem.site",
            "botem.site/",
            "localhost",
        )

        invalidDomains.forEach { domain ->
            val violations = validator.validate(AuthCookieProperties(domain = domain))

            assertEquals(1, violations.size, domain)
            assertEquals("domain", violations.single().propertyPath.toString(), domain)
        }
    }

    @Test
    fun `쿠키 도메인은 지정하지 않거나 올바른 도메인이어야 한다`() {
        listOf(null, ".botem.site", "botem.site", "api.botem.site").forEach { domain ->
            assertTrue(validator.validate(AuthCookieProperties(domain = domain)).isEmpty(), domain)
        }
    }
}
