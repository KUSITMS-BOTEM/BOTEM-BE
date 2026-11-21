package com.botem.domain.account.service

import com.botem.domain.account.entity.Bank
import com.botem.domain.account.entity.PayoutAccount
import com.botem.domain.account.repository.PayoutAccountRepository
import com.botem.domain.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.BDDMockito.given
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.data.domain.PageRequest
import kotlin.test.assertEquals

class PayoutAccountEncryptionRotationServiceTest {

    private val payoutAccountRepository = mock(PayoutAccountRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val accountNumberCipher = mock(AccountNumberCipher::class.java)
    private val service = PayoutAccountEncryptionRotationService(
        payoutAccountRepository,
        userRepository,
        accountNumberCipher,
    )

    @Test
    fun `계좌와 소유자를 ID 순서로 잠그고 이전 키 계좌만 재암호화한다`() {
        val first = payoutAccount(id = 10L, userId = 2L, encryptedAccountNumber = "v1:first")
        val second = payoutAccount(id = 11L, userId = 1L, encryptedAccountNumber = "v2:second")
        given(payoutAccountRepository.findIdsAfter(0L, PageRequest.of(0, 3))).willReturn(listOf(10L, 11L, 12L))
        given(payoutAccountRepository.findUserIdsByIdIn(listOf(10L, 11L))).willReturn(listOf(2L, 1L))
        given(payoutAccountRepository.findAllByIdInForUpdate(listOf(10L, 11L))).willReturn(listOf(first, second))
        given(accountNumberCipher.isEncryptedWithCurrentKey("v1:first")).willReturn(false)
        given(accountNumberCipher.isEncryptedWithCurrentKey("v2:second")).willReturn(true)
        given(accountNumberCipher.decrypt("v1:first")).willReturn("100012345678")
        given(accountNumberCipher.encrypt("100012345678")).willReturn("v2:rotated")

        val response = service.rotate(afterAccountId = null, batchSize = 2)

        assertEquals(2, response.processedCount)
        assertEquals(1, response.rotatedCount)
        assertEquals(11L, response.nextAfterAccountId)
        assertEquals("v2:rotated", first.encryptedAccountNumber)
        assertEquals("v2:second", second.encryptedAccountNumber)
        inOrder(payoutAccountRepository, userRepository).apply {
            verify(payoutAccountRepository).findUserIdsByIdIn(listOf(10L, 11L))
            verify(userRepository).findByIdForUpdate(1L)
            verify(userRepository).findByIdForUpdate(2L)
            verify(payoutAccountRepository).findAllByIdInForUpdate(listOf(10L, 11L))
        }
    }

    @Test
    fun `재실행 시 현재 키로 암호화된 계좌는 건너뛴다`() {
        val account = payoutAccount(id = 10L, userId = 1L, encryptedAccountNumber = "v2:already-rotated")
        given(payoutAccountRepository.findIdsAfter(10L, PageRequest.of(0, 2))).willReturn(listOf(10L))
        given(payoutAccountRepository.findUserIdsByIdIn(listOf(10L))).willReturn(listOf(1L))
        given(payoutAccountRepository.findAllByIdInForUpdate(listOf(10L))).willReturn(listOf(account))
        given(accountNumberCipher.isEncryptedWithCurrentKey(account.encryptedAccountNumber)).willReturn(true)

        val response = service.rotate(afterAccountId = 10L, batchSize = 1)

        assertEquals(1, response.processedCount)
        assertEquals(0, response.rotatedCount)
        assertEquals(null, response.nextAfterAccountId)
        verify(accountNumberCipher, never()).decrypt("v2:already-rotated")
        verify(accountNumberCipher, never()).encrypt("100012345678")
    }

    @Test
    fun `처리할 계좌가 없으면 잠금과 암호화 없이 완료한다`() {
        given(payoutAccountRepository.findIdsAfter(0L, PageRequest.of(0, 101))).willReturn(emptyList())

        val response = service.rotate(afterAccountId = null, batchSize = 100)

        assertEquals(0, response.processedCount)
        assertEquals(0, response.rotatedCount)
        assertEquals(null, response.nextAfterAccountId)
        verifyNoInteractions(userRepository, accountNumberCipher)
        verify(payoutAccountRepository, never()).findAllByIdInForUpdate(emptyList())
    }

    @Test
    fun `배치 크기가 범위를 벗어나면 거부한다`() {
        assertThrows<IllegalArgumentException> { service.rotate(afterAccountId = null, batchSize = 0) }
        assertThrows<IllegalArgumentException> { service.rotate(afterAccountId = null, batchSize = 501) }
    }

    private fun payoutAccount(id: Long, userId: Long, encryptedAccountNumber: String): PayoutAccount =
        PayoutAccount(
            userId = userId,
            bank = Bank.KAKAO_BANK,
            encryptedAccountNumber = encryptedAccountNumber,
            accountHolder = "김보탬",
            isDefault = true,
        ).also { account ->
            PayoutAccount::class.java.getDeclaredField("id").apply { isAccessible = true }.set(account, id)
        }
}
