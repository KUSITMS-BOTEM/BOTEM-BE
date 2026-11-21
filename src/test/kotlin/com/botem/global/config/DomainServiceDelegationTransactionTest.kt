package com.botem.global.config

import com.botem.domain.account.entity.Bank
import com.botem.domain.account.entity.PayoutAccount
import com.botem.domain.account.repository.PayoutAccountRepository
import com.botem.domain.account.service.AccountNumberCipher
import com.botem.domain.account.service.PayoutAccountService
import com.botem.domain.auth.entity.RefreshToken
import com.botem.domain.auth.repository.RefreshTokenRepository
import com.botem.domain.auth.service.RefreshTokenService
import com.botem.domain.user.entity.User
import com.botem.domain.user.repository.UserRepository
import com.botem.domain.user.service.UserService
import com.botem.domain.wish.service.WishService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

// 다른 도메인에 내준 잠금·저장·삭제 메서드는 호출한 쪽 트랜잭션 안에서만 의미가 있으므로
// 트랜잭션 밖에서 부르면 실행하지 않고 실패해야 한다 (Propagation.MANDATORY)
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(
    JpaAuditingConfig::class,
    DomainServiceTestConfig::class,
    RefreshTokenService::class,
    PayoutAccountService::class,
    DomainServiceDelegationTransactionTest.CipherConfig::class,
)
class DomainServiceDelegationTransactionTest {

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var wishService: WishService

    @Autowired
    private lateinit var refreshTokenService: RefreshTokenService

    @Autowired
    private lateinit var payoutAccountService: PayoutAccountService

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    private lateinit var payoutAccountRepository: PayoutAccountRepository

    @AfterEach
    fun tearDown() {
        refreshTokenRepository.deleteAll()
        payoutAccountRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `위임 메서드를 트랜잭션 밖에서 호출하면 실패한다`() {
        val user = User(kakaoId = 1L, nickname = "보탬", profileImageUrl = null)

        assertFailsWith<IllegalTransactionStateException> { userService.lockUser(1L) }
        assertFailsWith<IllegalTransactionStateException> { userService.lockActiveUser(1L) }
        assertFailsWith<IllegalTransactionStateException> { userService.createUser(user) }
        assertFailsWith<IllegalTransactionStateException> { userService.updateEmail(user, "new@kakao.com") }
        assertFailsWith<IllegalTransactionStateException> { wishService.lockOwnedWish(1L, 1L) }
        assertFailsWith<IllegalTransactionStateException> { refreshTokenService.deleteAllOf(1L) }
        assertFailsWith<IllegalTransactionStateException> { payoutAccountService.deleteAllOf(1L) }
    }

    @Test
    fun `호출한 쪽 트랜잭션 안에서는 그대로 실행된다`() {
        transactionTemplate.executeWithoutResult {
            assertNull(userService.lockUser(Long.MAX_VALUE))
            assertNull(wishService.lockOwnedWish(Long.MAX_VALUE, Long.MAX_VALUE))
            refreshTokenService.deleteAllOf(Long.MAX_VALUE)
            payoutAccountService.deleteAllOf(Long.MAX_VALUE)
        }
    }

    @Test
    fun `호출한 쪽 트랜잭션이 롤백되면 위임 메서드의 저장·변경·삭제도 함께 롤백된다`() {
        val existing = userRepository.save(User(kakaoId = 1L, nickname = "보탬", profileImageUrl = null, email = "old@kakao.com"))
        refreshTokenRepository.save(RefreshToken(userId = existing.id, tokenHash = "token-hash"))
        payoutAccountRepository.save(
            PayoutAccount(
                userId = existing.id,
                bank = Bank.KAKAO_BANK,
                encryptedAccountNumber = "encrypted",
                accountHolder = null,
                isDefault = true,
            ),
        )

        transactionTemplate.executeWithoutResult { status ->
            userService.createUser(User(kakaoId = 2L, nickname = "새회원", profileImageUrl = null))
            userService.updateEmail(userService.lockActiveUser(existing.id), "new@kakao.com")
            refreshTokenService.deleteAllOf(existing.id)
            payoutAccountService.deleteAllOf(existing.id)
            status.setRollbackOnly()
        }

        assertNull(userRepository.findByKakaoId(2L))
        assertEquals("old@kakao.com", userRepository.findById(existing.id).orElseThrow().email)
        assertTrue(refreshTokenRepository.findAll().any { it.userId == existing.id })
        assertEquals(1, payoutAccountRepository.findAll().count { it.userId == existing.id })
    }

    @TestConfiguration
    class CipherConfig {

        @Bean
        fun accountNumberCipher(): AccountNumberCipher = mock(AccountNumberCipher::class.java)
    }
}
