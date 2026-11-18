package com.botem.domain.funding.service

import com.botem.domain.account.entity.Bank
import com.botem.domain.account.service.AccountNumberCipher
import com.botem.domain.funding.dto.request.CreateContributionRequest
import com.botem.domain.funding.dto.request.MarkTransferSentRequest
import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.RollingPaperMessageRepository
import com.botem.domain.user.entity.User
import com.botem.domain.user.repository.UserRepository
import com.botem.global.config.JpaAuditingConfig
import com.botem.global.exception.BusinessException
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
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(
    JpaAuditingConfig::class,
    ContributionService::class,
    ContributionConcurrencyTest.FixedConfig::class,
)
class ContributionConcurrencyTest {

    @Autowired
    private lateinit var contributionService: ContributionService

    @Autowired
    private lateinit var fundingRepository: FundingRepository

    @Autowired
    private lateinit var contributionRepository: ContributionRepository

    @Autowired
    private lateinit var rollingPaperMessageRepository: RollingPaperMessageRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var cipher: AccountNumberCipher

    @AfterEach
    fun tearDown() {
        rollingPaperMessageRepository.deleteAll()
        contributionRepository.deleteAll()
        fundingRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `같은 요청 키로 동시에 보태면 참여를 하나만 만든다`() {
        val funding = saveFunding()

        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val futures = (1..2).map {
            executor.submit<Long> {
                start.await()
                contributionService.contribute(null, funding.id, request()).contributionId
            }
        }

        start.countDown()
        val contributionIds = futures.map { it.get(10, TimeUnit.SECONDS) }
        executor.shutdown()

        assertEquals(1, contributionIds.toSet().size)
        assertEquals(1, contributionRepository.count())
        assertEquals(1, rollingPaperMessageRepository.count())
    }

    @Test
    fun `비밀번호 실패 횟수는 예외가 나도 저장되어 연속 실패 시 잠긴다`() {
        val funding = saveFunding()
        val contributionId = contributionService.contribute(null, funding.id, request()).contributionId

        repeat(Contribution.MAX_GUEST_PASSWORD_FAILURES) {
            runCatching {
                contributionService.markTransferSent(null, funding.id, contributionId, MarkTransferSentRequest("9999"))
            }
        }

        val locked = contributionRepository.findById(contributionId).orElseThrow()
        assertNotNull(locked.guestPasswordLockedUntil)
        val failure = runCatching {
            contributionService.markTransferSent(null, funding.id, contributionId, MarkTransferSentRequest("1234"))
        }.exceptionOrNull()
        assertEquals(FundingErrorCode.CONTRIBUTION_PASSWORD_LOCKED, (failure as BusinessException).errorCode)
    }

    private fun saveFunding(): Funding {
        val owner = userRepository.save(User(kakaoId = 1L, nickname = "지유", profileImageUrl = null))

        return fundingRepository.save(
            Funding(
                ownerId = owner.id,
                recipientId = null,
                type = FundingType.BIRTHDAY,
                title = "생일 펀딩",
                targetAmount = 100_000L,
                deadline = NOW.plusDays(3),
                revealAt = null,
                story = "사연",
                promise = null,
                product = FundingProductSnapshot(
                    sourceWishId = null,
                    name = "에어팟",
                    imageUrl = "https://image.example.com/product.jpg",
                    price = 100_000L,
                    url = "https://shop.example.com/product",
                ),
                payoutAccount = FundingPayoutAccountSnapshot(
                    sourcePayoutAccountId = null,
                    bank = Bank.TOSS_BANK,
                    encryptedAccountNumber = cipher.encrypt("100012345678"),
                    accountHolder = "김지유",
                ),
            ),
        )
    }

    private fun request(): CreateContributionRequest =
        CreateContributionRequest(
            requestKey = "7f1c2a9e-3b4d-4e5f-8a6b-1c2d3e4f5a6b",
            participantName = "민지",
            amount = 10_000L,
            message = "생일 축하해!",
            paperTemplateCode = "BLUE_DOT_GRID",
            guestPassword = "1234",
        )

    @TestConfiguration
    class FixedConfig {

        @Bean
        fun clock(): Clock =
            Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), ZoneId.of("Asia/Seoul"))

        @Bean
        fun accountNumberCipher(): AccountNumberCipher =
            AccountNumberCipher(PayoutAccountEncryptionProperties(key = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="))
    }

    companion object {
        private val NOW = LocalDateTime.of(2026, 11, 18, 12, 0)
    }
}
