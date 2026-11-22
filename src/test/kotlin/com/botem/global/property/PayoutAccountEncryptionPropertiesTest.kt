package com.botem.global.property

import jakarta.validation.Validation
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PayoutAccountEncryptionPropertiesTest {

    private val validator = Validation.buildDefaultValidatorFactory().validator

    @Test
    fun `계좌 암호화 Key Ring의 모든 키는 Base64로 인코딩한 AES 키여야 한다`() {
        val invalidKeys = listOf(
            "not-base64",
            "c2hvcnQ=",
        )

        invalidKeys.forEach { key ->
            val violations = validator.validate(PayoutAccountEncryptionProperties(key))

            assertEquals(1, violations.size, key)
            assertEquals("keyRingValid", violations.single().propertyPath.toString(), key)
        }
    }

    @Test
    fun `32바이트 계좌 암호화 키를 허용한다`() {
        val properties = PayoutAccountEncryptionProperties(
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        )

        assertTrue(validator.validate(properties).isEmpty())
    }

    @Test
    fun `현재 키와 레거시 키가 Key Ring에 없으면 거부한다`() {
        val invalidKeyRings = listOf(
            // 현재 키 누락
            PayoutAccountEncryptionProperties(currentKeyId = "v2", legacyKeyId = "v1", keys = mapOf("v1" to VALID_KEY)),
            // 레거시 키 누락: 버전 없는 기존 암호문을 복호화할 수 없다
            PayoutAccountEncryptionProperties(currentKeyId = "v2", legacyKeyId = "v1", keys = mapOf("v2" to VALID_KEY)),
            PayoutAccountEncryptionProperties(currentKeyId = "v1", legacyKeyId = "v1", keys = emptyMap()),
        )

        invalidKeyRings.forEach { properties ->
            val violations = validator.validate(properties)

            assertEquals(1, violations.size, properties.keys.keys.toString())
            assertEquals("keyRingValid", violations.single().propertyPath.toString())
        }
    }

    @Test
    fun `키 식별자가 잘못되었거나 키가 비어 있으면 거부한다`() {
        val invalidKeyId = PayoutAccountEncryptionProperties(
            currentKeyId = "current key",
            legacyKeyId = "v1",
            keys = mapOf("current key" to VALID_KEY, "v1" to VALID_KEY),
        )
        val blankKey = PayoutAccountEncryptionProperties(
            currentKeyId = "v1",
            legacyKeyId = "v1",
            keys = mapOf("v1" to " "),
        )

        assertEquals("keyRingValid", validator.validate(invalidKeyId).single().propertyPath.toString())
        assertEquals("keyRingValid", validator.validate(blankKey).single().propertyPath.toString())
    }

    @Test
    fun `없는 키 식별자를 조회하면 원문 키 정보를 노출하지 않고 거부한다`() {
        val properties = PayoutAccountEncryptionProperties(VALID_KEY)

        val exception = assertFailsWith<IllegalArgumentException> { properties.keyOf("v2") }

        assertEquals("계좌 암호화 키를 찾을 수 없습니다.", exception.message)
    }

    companion object {
        private const val VALID_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
    }
}
