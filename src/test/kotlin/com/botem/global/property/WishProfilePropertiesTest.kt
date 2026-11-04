package com.botem.global.property

import jakarta.validation.Validation
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WishProfilePropertiesTest {

    private val validator = Validation.buildDefaultValidatorFactory().validator

    @Test
    fun `위시 프로필 기본 주소는 공백일 수 없다`() {
        val violations = validator.validate(WishProfileProperties(baseUrl = " "))

        assertEquals(1, violations.size)
        assertEquals("baseUrl", violations.single().propertyPath.toString())
    }

    @Test
    fun `위시 프로필 기본 주소는 올바른 절대 URL이어야 한다`() {
        val invalidUrls = listOf(
            "botem.site",
            "ftp://botem.site",
            "https://botem.site?ref=campaign",
            "https://botem.site#profile",
        )

        invalidUrls.forEach { baseUrl ->
            val violations = validator.validate(WishProfileProperties(baseUrl = baseUrl))

            assertEquals(1, violations.size, baseUrl)
            assertEquals("baseUrlValid", violations.single().propertyPath.toString(), baseUrl)
        }
    }

    @Test
    fun `위시 프로필 기본 주소는 경로와 마지막 슬래시를 포함할 수 있다`() {
        val violations = validator.validate(
            WishProfileProperties(baseUrl = "https://botem.site/wish/"),
        )

        assertTrue(violations.isEmpty())
    }
}
