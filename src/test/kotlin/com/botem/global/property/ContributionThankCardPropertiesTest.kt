package com.botem.global.property

import jakarta.validation.Validation
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContributionThankCardPropertiesTest {

    private val validator = Validation.buildDefaultValidatorFactory().validator

    @Test
    fun `감사카드 공유 기본 주소는 공백일 수 없다`() {
        val violations = validator.validate(ContributionThankCardProperties(" "))

        assertEquals(1, violations.size)
        assertEquals("baseUrl", violations.single().propertyPath.toString())
    }

    @Test
    fun `감사카드 공유 기본 주소는 올바른 절대 URL이어야 한다`() {
        listOf(
            "botem.me/thank-cards",
            "ftp://botem.me/thank-cards",
            "https://botem.me/thank-cards?ref=share",
            "https://botem.me/thank-cards#card",
        ).forEach { baseUrl ->
            val violations = validator.validate(ContributionThankCardProperties(baseUrl))

            assertEquals(1, violations.size, baseUrl)
            assertEquals("baseUrlValid", violations.single().propertyPath.toString(), baseUrl)
        }
    }

    @Test
    fun `감사카드 공유 기본 주소는 마지막 슬래시를 포함할 수 있다`() {
        assertTrue(validator.validate(ContributionThankCardProperties("https://botem.me/thank-cards/")).isEmpty())
    }
}
