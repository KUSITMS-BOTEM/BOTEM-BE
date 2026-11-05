package com.botem.domain.account.service

import com.botem.global.property.PayoutAccountEncryptionProperties
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

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
}
