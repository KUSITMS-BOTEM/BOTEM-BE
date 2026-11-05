package com.botem.global.property

import jakarta.validation.Validation
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PayoutAccountEncryptionPropertiesTest {

    private val validator = Validation.buildDefaultValidatorFactory().validator

    @Test
    fun `계좌 암호화 키는 Base64로 인코딩한 AES 키여야 한다`() {
        val invalidKeys = listOf(
            "not-base64",
            "c2hvcnQ=",
        )

        invalidKeys.forEach { key ->
            val violations = validator.validate(PayoutAccountEncryptionProperties(key))

            assertEquals(1, violations.size, key)
            assertEquals("keyValid", violations.single().propertyPath.toString(), key)
        }
    }

    @Test
    fun `32바이트 계좌 암호화 키를 허용한다`() {
        val properties = PayoutAccountEncryptionProperties(
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        )

        assertTrue(validator.validate(properties).isEmpty())
    }
}
