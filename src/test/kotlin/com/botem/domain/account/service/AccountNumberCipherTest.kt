package com.botem.domain.account.service

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

        assertFailsWith<IllegalArgumentException> {
            keyRingCipher.decrypt("v3:encrypted-payload")
        }
        assertFailsWith<IllegalArgumentException> {
            keyRingCipher.decrypt("v1:not-base64")
        }
        assertFailsWith<IllegalArgumentException> {
            keyRingCipher.decrypt("v1:MTIz")
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
