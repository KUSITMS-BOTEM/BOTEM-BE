package com.botem.domain.account.service

import com.botem.domain.account.entity.Bank
import com.botem.domain.account.entity.PayoutAccount
import com.botem.domain.account.repository.PayoutAccountRepository
import com.botem.domain.user.entity.User
import com.botem.domain.user.repository.UserRepository
import com.botem.global.config.JpaAuditingConfig
import com.botem.global.property.PayoutAccountEncryptionProperties
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(
    JpaAuditingConfig::class,
    PayoutAccountEncryptionRotationService::class,
    PayoutAccountEncryptionRotationPersistenceTest.KeyRingConfig::class,
)
class PayoutAccountEncryptionRotationPersistenceTest {

    @Autowired
    private lateinit var service: PayoutAccountEncryptionRotationService

    @Autowired
    private lateinit var payoutAccountRepository: PayoutAccountRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var accountNumberCipher: AccountNumberCipher

    @AfterEach
    fun tearDown() {
        payoutAccountRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `버전 없는 레거시 암호문을 현재 키로 재암호화하고 같은 배치를 다시 실행해도 안전하다`() {
        val user = saveUser(1L)
        val legacyCiphertext = legacyCipher().encrypt("3333012345678").substringAfter("v1:")
        val account = saveAccount(user.id, legacyCiphertext)

        val first = service.rotate(afterAccountId = null, batchSize = 100)
        val rotatedAccount = payoutAccountRepository.findById(account.id).orElseThrow()
        val second = service.rotate(afterAccountId = null, batchSize = 100)

        assertEquals(1, first.processedCount)
        assertEquals(1, first.rotatedCount)
        assertNull(first.nextAfterAccountId)
        assertTrue(rotatedAccount.encryptedAccountNumber.startsWith("v2:"))
        assertEquals("3333012345678", accountNumberCipher.decrypt(rotatedAccount.encryptedAccountNumber))
        assertEquals(1, second.processedCount)
        assertEquals(0, second.rotatedCount)
    }

    @Test
    fun `커서로 나누어 실행하면 중단 지점 다음 계좌부터 재개한다`() {
        val firstAccount = saveAccount(saveUser(1L).id, legacyCipher().encrypt("3333012345678"))
        val secondAccount = saveAccount(saveUser(2L).id, legacyCipher().encrypt("3333012345679"))

        val first = service.rotate(afterAccountId = null, batchSize = 1)
        val afterFirst = payoutAccountRepository.findById(firstAccount.id).orElseThrow()
        val beforeSecond = payoutAccountRepository.findById(secondAccount.id).orElseThrow()
        val second = service.rotate(afterAccountId = first.nextAfterAccountId, batchSize = 1)
        val afterSecond = payoutAccountRepository.findById(secondAccount.id).orElseThrow()

        assertEquals(firstAccount.id, first.nextAfterAccountId)
        assertEquals(1, first.rotatedCount)
        assertTrue(afterFirst.encryptedAccountNumber.startsWith("v2:"))
        assertFalse(beforeSecond.encryptedAccountNumber.startsWith("v2:"))
        assertNull(second.nextAfterAccountId)
        assertEquals(1, second.rotatedCount)
        assertTrue(afterSecond.encryptedAccountNumber.startsWith("v2:"))
    }

    private fun saveUser(kakaoId: Long): User =
        userRepository.saveAndFlush(User(kakaoId = kakaoId, nickname = "보탬$kakaoId", profileImageUrl = null))

    private fun saveAccount(userId: Long, encryptedAccountNumber: String): PayoutAccount =
        payoutAccountRepository.saveAndFlush(
            PayoutAccount(
                userId = userId,
                bank = Bank.KAKAO_BANK,
                encryptedAccountNumber = encryptedAccountNumber,
                accountHolder = "김보탬",
                isDefault = true,
            ),
        )

    private fun legacyCipher(): AccountNumberCipher =
        AccountNumberCipher(PayoutAccountEncryptionProperties(key = LEGACY_KEY))

    @TestConfiguration
    class KeyRingConfig {

        @Bean
        fun accountNumberCipher(): AccountNumberCipher =
            AccountNumberCipher(
                PayoutAccountEncryptionProperties(
                    currentKeyId = "v2",
                    legacyKeyId = "v1",
                    keys = mapOf("v1" to LEGACY_KEY, "v2" to CURRENT_KEY),
                ),
            )
    }

    companion object {
        private const val LEGACY_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
        private const val CURRENT_KEY = "ZmVkY2JhOTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTA="
    }
}
