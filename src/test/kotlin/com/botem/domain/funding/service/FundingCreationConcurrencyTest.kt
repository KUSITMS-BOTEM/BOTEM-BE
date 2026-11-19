package com.botem.domain.funding.service

import com.botem.domain.account.entity.Bank
import com.botem.domain.account.entity.PayoutAccount
import com.botem.domain.account.repository.PayoutAccountRepository
import com.botem.domain.account.service.AccountNumberCipher
import com.botem.domain.funding.dto.request.CreateBirthdayFundingRequest
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.user.entity.User
import com.botem.domain.user.repository.UserRepository
import com.botem.domain.wish.entity.Wish
import com.botem.domain.wish.repository.WishRepository
import com.botem.global.config.JpaAuditingConfig
import com.botem.global.exception.BusinessException
import com.botem.global.property.FundingShareProperties
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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertIs

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(
    JpaAuditingConfig::class,
    FundingCreationService::class,
    FundingCreationResourceResolver::class,
    FundingCreationConcurrencyTest.FixedConfig::class,
)
class FundingCreationConcurrencyTest {

    @Autowired
    private lateinit var fundingCreationService: FundingCreationService

    @Autowired
    private lateinit var fundingRepository: FundingRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var wishRepository: WishRepository

    @Autowired
    private lateinit var payoutAccountRepository: PayoutAccountRepository

    @AfterEach
    fun tearDown() {
        fundingRepository.deleteAll()
        wishRepository.deleteAll()
        payoutAccountRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `활성 펀딩이 네 개일 때 동시 개설 요청 중 하나만 성공한다`() {
        val user = userRepository.save(User(kakaoId = 1L, nickname = "지유", profileImageUrl = null))
        val payoutAccount = payoutAccountRepository.save(
            PayoutAccount(
                userId = user.id,
                bank = Bank.KAKAO_BANK,
                encryptedAccountNumber = "encrypted-account-number",
                accountHolder = "지유",
                isDefault = true,
            ),
        )
        val wishes = wishRepository.saveAll(
            listOf(
                wish(user.id, "첫 번째 상품"),
                wish(user.id, "두 번째 상품"),
            ),
        )
        fundingRepository.saveAll(
            (1..4).map { index -> existingFunding(user.id, payoutAccount, index) },
        )

        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val futures = wishes.map { wish ->
            executor.submit<Throwable?> {
                start.await()
                runCatching {
                    fundingCreationService.createBirthdayFunding(user.id, request(wish.id))
                }.exceptionOrNull()
            }
        }

        start.countDown()
        val results = futures.map { it.get(10, TimeUnit.SECONDS) }
        executor.shutdown()

        assertEquals(1, results.count { it == null })
        val failure = results.single { it != null }
        assertIs<BusinessException>(failure)
        assertEquals(FundingErrorCode.ACTIVE_FUNDING_LIMIT_EXCEEDED, failure.errorCode)
        assertEquals(5L, fundingRepository.countActiveFundings(user.id, NOW))
    }

    private fun request(wishId: Long): CreateBirthdayFundingRequest =
        CreateBirthdayFundingRequest(
            wishId = wishId,
            bank = Bank.TOSS_BANK,
            accountNumber = "100159014821",
            targetAmount = 100_000L,
            deadline = LocalDate.of(2026, 11, 30),
            story = "새 선물이 필요해요",
            promise = null,
        )

    private fun wish(userId: Long, productName: String): Wish =
        Wish(
            userId = userId,
            productName = productName,
            productImageUrl = "https://image.example.com/product.jpg",
            price = 100_000L,
            productUrl = "https://shop.example.com/product",
        )

    private fun existingFunding(
        ownerId: Long,
        payoutAccount: PayoutAccount,
        index: Int,
    ): Funding =
        Funding(
            ownerId = ownerId,
            recipientId = null,
            type = FundingType.BIRTHDAY,
            title = "기존 펀딩 $index",
            targetAmount = 100_000L,
            deadline = NOW.plusDays(index.toLong()),
            revealAt = null,
            story = "기존 펀딩 사연",
            promise = null,
            product = FundingProductSnapshot(
                sourceWishId = null,
                name = "기존 상품 $index",
                imageUrl = "https://image.example.com/$index.jpg",
                price = 100_000L,
                url = "https://shop.example.com/$index",
            ),
            payoutAccount = FundingPayoutAccountSnapshot(
                sourcePayoutAccountId = payoutAccount.id,
                bank = payoutAccount.bank,
                encryptedAccountNumber = payoutAccount.encryptedAccountNumber,
                accountHolder = payoutAccount.accountHolder,
            ),
        )

    @TestConfiguration
    class FixedConfig {

        @Bean
        fun clock(): Clock =
            Clock.fixed(Instant.parse("2026-11-17T03:00:00Z"), ZoneId.of("Asia/Seoul"))

        @Bean
        fun fundingShareProperties(): FundingShareProperties =
            FundingShareProperties("https://botem.me/fundings")

        @Bean
        fun accountNumberCipher(): AccountNumberCipher =
            AccountNumberCipher(PayoutAccountEncryptionProperties(key = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="))
    }

    companion object {
        private val NOW = LocalDateTime.of(2026, 11, 17, 12, 0)
    }
}
