package com.botem.domain.funding.service

import com.botem.domain.account.entity.Bank
import com.botem.domain.funding.dto.request.SendContributionThankCardRequest
import com.botem.domain.funding.dto.response.ContributionThankCardResponse
import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.ContributionThankCardRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.user.entity.User
import com.botem.domain.user.repository.UserRepository
import com.botem.global.config.JpaAuditingConfig
import com.botem.global.property.ContributionThankCardProperties
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

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(
    JpaAuditingConfig::class,
    ContributionThankCardService::class,
    ContributionThankCardConcurrencyTest.FixedConfig::class,
)
class ContributionThankCardConcurrencyTest {

    @Autowired
    private lateinit var service: ContributionThankCardService

    @Autowired
    private lateinit var fundingRepository: FundingRepository

    @Autowired
    private lateinit var contributionRepository: ContributionRepository

    @Autowired
    private lateinit var thankCardRepository: ContributionThankCardRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @AfterEach
    fun tearDown() {
        thankCardRepository.deleteAll()
        contributionRepository.deleteAll()
        fundingRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `같은 보탬에 동시에 감사카드를 보내도 한 장만 저장한다`() {
        val owner = userRepository.save(User(kakaoId = 1L, nickname = "지유", profileImageUrl = null))
        val participant = userRepository.save(User(kakaoId = 2L, nickname = "하린", profileImageUrl = null))
        val funding = fundingRepository.save(funding(owner.id))
        val contribution = contributionRepository.save(
            Contribution(
                fundingId = funding.id,
                participantUserId = participant.id,
                participantName = "하린",
                amount = 10_000L,
                status = ContributionStatus.AWAITING_TRANSFER,
                guestPasswordHash = null,
            ).also {
                it.markTransferSent()
                it.confirm(NOW.minusHours(1))
            },
        )

        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val futures = (1..2).map {
            executor.submit<ContributionThankCardResponse> {
                start.await()
                service.send(
                    owner.id,
                    funding.id,
                    contribution.id,
                    SendContributionThankCardRequest("고마워"),
                )
            }
        }

        start.countDown()
        val responses = futures.map { it.get(10, TimeUnit.SECONDS) }
        executor.shutdown()

        assertEquals(1L, thankCardRepository.count())
        assertEquals(1, responses.map { it.thankCardId }.toSet().size)
        assertEquals(1, responses.map { it.shareUrl }.toSet().size)
    }

    private fun funding(ownerId: Long): Funding =
        Funding(
            ownerId = ownerId,
            recipientId = null,
            type = FundingType.BIRTHDAY,
            title = "지유의 생일",
            targetAmount = 100_000L,
            deadline = NOW.plusDays(1),
            revealAt = null,
            story = null,
            promise = null,
            product = FundingProductSnapshot(
                sourceWishId = null,
                name = "무선 헤드폰",
                imageUrl = "https://image.example.com/product.jpg",
                price = 100_000L,
                url = "https://shop.example.com/product",
            ),
            payoutAccount = FundingPayoutAccountSnapshot(null, Bank.TOSS_BANK, "encrypted", "지유"),
        )

    @TestConfiguration
    class FixedConfig {

        @Bean
        fun clock(): Clock =
            Clock.fixed(Instant.parse("2026-11-19T11:00:00Z"), ZoneId.of("Asia/Seoul"))

        @Bean
        fun contributionThankCardProperties(): ContributionThankCardProperties =
            ContributionThankCardProperties("https://botem.me/thank-cards")
    }

    companion object {
        private val NOW = LocalDateTime.of(2026, 11, 19, 20, 0)
    }
}
