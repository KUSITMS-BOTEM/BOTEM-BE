package com.botem.global.property

import jakarta.validation.Validation
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FundingSharePropertiesTest {

    private val validator = Validation.buildDefaultValidatorFactory().validator

    @Test
    fun `펀딩 공유 기본 주소는 공백일 수 없다`() {
        val violations = validator.validate(FundingShareProperties(baseUrl = " "))

        assertEquals(1, violations.size)
        assertEquals("baseUrl", violations.single().propertyPath.toString())
    }

    @Test
    fun `펀딩 공유 기본 주소는 올바른 절대 URL이어야 한다`() {
        listOf(
            "botem.me/fundings",
            "ftp://botem.me/fundings",
            "https://botem.me/fundings?ref=share",
            "https://botem.me/fundings#result",
        ).forEach { baseUrl ->
            val violations = validator.validate(FundingShareProperties(baseUrl))

            assertEquals(1, violations.size, baseUrl)
            assertEquals("baseUrlValid", violations.single().propertyPath.toString(), baseUrl)
        }
    }

    @Test
    fun `펀딩 공유 기본 주소는 경로와 마지막 슬래시를 포함할 수 있다`() {
        assertTrue(validator.validate(FundingShareProperties("https://botem.me/fundings/")).isEmpty())
    }
}
