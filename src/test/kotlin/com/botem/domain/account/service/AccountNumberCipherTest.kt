package com.botem.domain.account.service

import com.botem.domain.account.exception.PayoutAccountErrorCode
import com.botem.global.exception.BusinessException
import com.botem.global.property.PayoutAccountEncryptionProperties
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AccountNumberCipherTest {

    private val cipher = AccountNumberCipher(
        PayoutAccountEncryptionProperties(
            key = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        ),
    )

    @Test
    fun `계좌번호를 암호화하고 복호화한다`() {
        val accountNumber = "100012345678"

        val encrypted = cipher.encrypt(accountNumber)

        assertNotEquals(accountNumber, encrypted)
        assertEquals(accountNumber, cipher.decrypt(encrypted))
    }

    @Test
    fun `같은 계좌번호도 매번 다른 암호문으로 저장한다`() {
        val accountNumber = "100012345678"

        val first = cipher.encrypt(accountNumber)
        val second = cipher.encrypt(accountNumber)

        assertNotEquals(first, second)
        assertEquals(accountNumber, cipher.decrypt(first))
        assertEquals(accountNumber, cipher.decrypt(second))
    }

    @Test
    fun `이전 키와 버전 없는 기존 암호문을 복호화하고 현재 키로 식별한다`() {
        val keyRingCipher = AccountNumberCipher(
            PayoutAccountEncryptionProperties(
                currentKeyId = "v2",
                legacyKeyId = "v1",
                keys = mapOf(
                    "v1" to KEY,
                    "v2" to "ZmVkY2JhOTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTA=",
                ),
            ),
        )
        val encryptedWithPreviousKey = cipher.encrypt("100012345678")
        val unversionedLegacyCiphertext = encryptedWithPreviousKey.substringAfter("v1:")

        assertEquals("100012345678", keyRingCipher.decrypt(encryptedWithPreviousKey))
        assertEquals("100012345678", keyRingCipher.decrypt(unversionedLegacyCiphertext))
        assertFalse(keyRingCipher.isEncryptedWithCurrentKey(encryptedWithPreviousKey))

        val encryptedWithCurrentKey = keyRingCipher.encrypt("100012345678")

        assertTrue(encryptedWithCurrentKey.startsWith("v2:"))
        assertTrue(keyRingCipher.isEncryptedWithCurrentKey(encryptedWithCurrentKey))
    }

    @Test
    fun `등록되지 않은 키 버전이나 잘못된 암호문은 복호화하지 않는다`() {
        val keyRingCipher = keyRingCipher()
        // 형식이 올바른 암호문이어야 Base64 해석이 아니라 키 조회 단계에서 거부되는지 확인할 수 있다
        val encryptedWithUnknownKey = AccountNumberCipher(
            PayoutAccountEncryptionProperties(
                currentKeyId = "v3",
                legacyKeyId = "v3",
                keys = mapOf("v3" to "YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk="),
            ),
        ).encrypt("100012345678")

        assertFailsWith<IllegalArgumentException> {
            keyRingCipher.decrypt(encryptedWithUnknownKey)
        }
        assertFailsWith<IllegalArgumentException> {
            keyRingCipher.decrypt("v1:not-base64")
        }
        assertFailsWith<IllegalArgumentException> {
            keyRingCipher.decrypt("v1:MTIz")
        }
    }

    @Test
    fun `숫자만 남긴 계좌번호가 8자리 이상 20자리 이하일 때만 암호화한다`() {
        assertEquals("12345678", cipher.decrypt(cipher.normalizeAndEncrypt("1234-5678")))
        assertEquals("1".repeat(20), cipher.decrypt(cipher.normalizeAndEncrypt("1".repeat(20))))

        listOf("1234567", "123-45 67", "1".repeat(21)).forEach { accountNumber ->
            val exception = assertFailsWith<BusinessException>(accountNumber) {
                cipher.normalizeAndEncrypt(accountNumber)
            }
            assertEquals(PayoutAccountErrorCode.INVALID_ACCOUNT_NUMBER, exception.errorCode)
        }
    }

    private fun keyRingCipher(): AccountNumberCipher =
        AccountNumberCipher(
            PayoutAccountEncryptionProperties(
                currentKeyId = "v2",
                legacyKeyId = "v1",
                keys = mapOf(
                    "v1" to KEY,
                    "v2" to "ZmVkY2JhOTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTA=",
                ),
            ),
        )

    companion object {
        private const val KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
    }
}
