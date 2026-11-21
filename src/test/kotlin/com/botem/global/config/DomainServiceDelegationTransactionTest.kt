package com.botem.global.config

import com.botem.domain.account.service.AccountNumberCipher
import com.botem.domain.account.service.PayoutAccountService
import com.botem.domain.auth.service.RefreshTokenService
import com.botem.domain.user.entity.User
import com.botem.domain.user.service.UserService
import com.botem.domain.wish.service.WishService
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
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

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

    @TestConfiguration
    class CipherConfig {

        @Bean
        fun accountNumberCipher(): AccountNumberCipher = mock(AccountNumberCipher::class.java)
    }
}
