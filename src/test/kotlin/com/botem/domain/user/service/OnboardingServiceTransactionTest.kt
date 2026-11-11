package com.botem.domain.user.service

import com.botem.domain.account.entity.Bank
import com.botem.domain.account.exception.PayoutAccountErrorCode
import com.botem.domain.account.repository.PayoutAccountRepository
import com.botem.domain.account.service.AccountNumberCipher
import com.botem.domain.account.service.PayoutAccountService
import com.botem.domain.user.dto.request.RegisterOnboardingRequest
import com.botem.domain.user.entity.User
import com.botem.domain.user.repository.UserRepository
import com.botem.global.config.JpaAuditingConfig
import com.botem.global.config.S3Config
import com.botem.global.exception.BusinessException
import com.botem.global.property.PayoutAccountEncryptionProperties
import com.botem.global.property.S3Properties
import com.botem.global.property.WishProfileProperties
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.MonthDay
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

// 서비스 트랜잭션이 실제로 커밋·롤백되는지 확인하기 위해 테스트 트랜잭션을 사용하지 않는다
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(
    JpaAuditingConfig::class,
    OnboardingService::class,
    ProfileImageService::class,
    S3Config::class,
    UserService::class,
    PayoutAccountService::class,
    AccountNumberCipher::class,
)
@EnableConfigurationProperties(
    WishProfileProperties::class,
    PayoutAccountEncryptionProperties::class,
    S3Properties::class,
)
class OnboardingServiceTransactionTest {

    @Autowired
    private lateinit var onboardingService: OnboardingService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var payoutAccountRepository: PayoutAccountRepository

    @AfterEach
    fun tearDown() {
        payoutAccountRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `계좌 등록에 실패하면 이름과 생일도 저장되지 않는다`() {
        val user = userRepository.save(User(kakaoId = 1L, nickname = "지유", profileImageUrl = null))

        val exception = assertFailsWith<BusinessException> {
            onboardingService.completeOnboarding(
                user.id,
                RegisterOnboardingRequest(
                    name = "이정민",
                    birthday = MonthDay.of(10, 13),
                    bank = Bank.TOSS_BANK,
                    accountNumber = "12",
                ),
            )
        }

        val savedUser = userRepository.findById(user.id).orElseThrow()
        assertEquals(PayoutAccountErrorCode.INVALID_ACCOUNT_NUMBER, exception.errorCode)
        assertNull(savedUser.name)
        assertNull(savedUser.birthday)
        assertFalse(payoutAccountRepository.existsByUserId(user.id))
    }
}
