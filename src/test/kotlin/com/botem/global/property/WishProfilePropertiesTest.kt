package com.botem.global.property

import jakarta.validation.Validation
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class WishProfilePropertiesTest {

    private val validator = Validation.buildDefaultValidatorFactory().validator

    @Test
    fun `위시 프로필 기본 주소는 공백일 수 없다`() {
        val violations = validator.validate(WishProfileProperties(baseUrl = " "))

        assertEquals(1, violations.size)
        assertEquals("baseUrl", violations.single().propertyPath.toString())
    }
}
